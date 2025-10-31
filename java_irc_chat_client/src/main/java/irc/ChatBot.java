package irc;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jibble.pircbot.DccFileTransfer;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java_irc_chat_client.Canal;
import java_irc_chat_client.CanalController;
import java_irc_chat_client.ChatController;
import javafx.application.Platform;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


/**
 * ChatBot extiende PircBot 1.5.0 y maneja la conexión, eventos IRC y DCC,
 * fusionando toda la lógica de la antigua IrcEventListener.
 */
public class ChatBot extends PircBot {
    private static final Logger log = LoggerFactory.getLogger(ChatBot.class);
    private final ChatController mainController;
    
    // --- CAMPOS PARA LA LISTA DE CANALES ---
    private Consumer<Canal> currentListReceiver;
    private Runnable currentListEndCallback;
    private boolean isListingChannels = false;
    private final Set<String> joinedChannels = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, CanalController> messageDelegates = new ConcurrentHashMap<>();
    
    // ⭐ El Nickname debe coincidir con el utilizado en onServerResponse para el parseo
    private static final String NICKNAME = "Takkiles4321";
    //private static final String NICKNAME = "Sakkiles4321";
    
    // ⭐ DCC: Mapa para guardar el callback de progreso para CADA NICK (Lado Emisor)
    private final Map<String, BiConsumer<Long, Long>> dccProgressConsumers = new HashMap<>();
    
    private final Map<String, CanalController> namesDelegates = new ConcurrentHashMap<>();
    
    public void registerMessageDelegate(String channel, CanalController controller) {
        messageDelegates.put(channel.toLowerCase(), controller);
    }

    public void registerNamesDelegate(String channel, CanalController controller) {
        if (controller == null) {
            namesDelegates.remove(channel); // Desregistro
        } else {
            namesDelegates.put(channel, controller); // Registro
        }
    }
    
    public ChatBot(ChatController controller, String nickname) {
        this.mainController = controller;
        this.setName(nickname);
        
        this.setVerbose(true); 
        this.setLogin(nickname); 
        this.setFinger("JIRCHAT Client"); 
       // this.setAutoNickChange(true);
        try {
            setEncoding("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // En caso de fallo (extremadamente raro), registra el error y usa el encoding por defecto
            log.error("❌ Fallo al establecer el encoding 'UTF-8'. Usando el encoding por defecto.", e);
            // Podrías lanzar una RuntimeException o dejar que el bot continúe con el default.
        }
        
        // ==========================================================
        // ⭐ DCC FIX: Configuración de Puertos Dinámica
        // ==========================================================

        // 1. Leer la propiedad de sistema 'dcc.port.base'. 
        int portBase;
        try {
            portBase = Integer.parseInt(System.getProperty("dcc.port.base", "50000"));
        } catch (NumberFormatException e) {
            portBase = 50000;
            log.error("Propiedad dcc.port.base no válida. Usando el puerto base por defecto: 50000");
        }

        // Definir el rango (tamaño de 51 puertos)
        final int RANGE_SIZE = 50; 
        int MIN_PORT = portBase;
        int MAX_PORT = portBase + RANGE_SIZE;
        
        // 2. Crear el array de puertos requerido por PircBot 1.5.0
        int numPorts = MAX_PORT - MIN_PORT + 1;
        int[] dccPorts = new int[numPorts];
        
        for (int i = 0; i < numPorts; i++) {
            dccPorts[i] = MIN_PORT + i;
        }

        // 3. Aplicar el array de puertos
        this.setDccPorts(dccPorts);
        log.warn("DCC FIX: Rango de puertos DCC asignado: {}-{} para nick: {}", MIN_PORT, MAX_PORT, nickname);
        
        
        
        // ==========================================================
        // ⭐ DCC FIX: Forzar IP a Loopback (127.0.0.1)
        // ==========================================================

        try {
            java.net.InetAddress loopback = java.net.InetAddress.getByName("127.0.0.1"); 
            this.setDccInetAddress(loopback);
            log.warn("DCC FIX: Forzando IP de DCC a Loopback (127.0.0.1) para prueba local.");
        } catch (Exception e) {
            log.error("DCC FIX: Error al configurar la IP de Loopback.", e);
        }
        
        log.info("🤖 ChatBot (PircBot 1.5.0) inicializado con nick: {}", nickname);
    }
    
    public boolean isJoined(String channel) {
        // El método containsIgnoreCase es útil si manejas mayúsculas/minúsculas de canales.
        return joinedChannels.contains(channel.toLowerCase()); 
    }
    
    
    // ==========================================================
    // MÉTODOS DCC - LADO EMISOR (Llamados desde PrivadoController)
    // ==========================================================

    /**
     * Registra el callback de UI para actualizar la barra de progreso.
     * @param nick El destinatario de la transferencia.
     * @param consumer La función (transferido, total) para actualizar la UI.
     */
    public void registerDccProgressConsumer(String nick, BiConsumer<Long, Long> consumer) {
        log.debug("DCC Register: Registrando consumidor para el nick: {}", nick);
        dccProgressConsumers.put(nick.toLowerCase(), consumer);
    }
    
    /**
     * Inicia el envío de un archivo DCC e inicia el hilo de polling para el progreso.
     */
    public void sendDccFile(File file, String nick, int timeout) {
        log.debug("DCC Send: Solicitado envío de archivo '{}' a {} (Tamaño: {} bytes)", file.getName(), nick, file.length());
        
        // 1. Inicia el envío DCC (nativo de PircBot). PircBot abre el ServerSocket aquí.
        DccFileTransfer transfer = this.dccSendFile(file, nick, timeout);

        if (transfer == null) {
            log.error("DCC Send: dccSendFile retornó null. Conexión/negociación fallida. Revise logs de PircBot para ver si hubo un error de Bind o Timeout.");
            Platform.runLater(() -> mainController.appendSystemMessage("❌ Error al iniciar DCC. Conexión/negociación fallida."));
            return;
        }
        
        // ⭐ CORRECCIÓN CLAVE: Aplicar el retraso para estabilizar la conexión Loopback.
        // setPacketDelay pertenece a la clase DccFileTransfer.
        transfer.setPacketDelay(50L);
        log.warn("DCC FIX: Packet Delay aplicado a la transferencia saliente (50ms) para estabilizar Loopback.");

        log.debug("DCC Send: Transferencia DCC iniciada. Objeto transfer: {}", transfer);


        // 2. Inicia el hilo de polling para el progreso
        String targetNick = transfer.getNick();
        BiConsumer<Long, Long> consumer = dccProgressConsumers.get(targetNick.toLowerCase());

        if (consumer != null) {
            log.debug("DCC Polling: Iniciando hilo de polling para {}", targetNick);
            new Thread(() -> {
                long total = transfer.getSize();
                boolean finished = false;
                long lastTransferred = -1; // Para evitar spam de logs si el progreso se detiene

                // Bucle principal para monitorear el progreso
                while (!finished) { 
                    
                    long transferred = transfer.getProgress(); 
                    
                    if (transferred != lastTransferred) {
                        log.debug("DCC Polling: {}/{} bytes transferidos ({}%)", transferred, total, (total > 0 ? (transferred * 100 / total) : 0));
                        lastTransferred = transferred;
                    }

                    // Notificamos a la UI
                    consumer.accept(transferred, total);
                    
                    // CONDICIÓN DE SALIDA: Si el progreso alcanza el tamaño total
                    if (transferred >= total && total > 0) {
                        log.debug("DCC Polling: Progreso al 100%. Finalizando bucle.");
                        finished = true;
                    }
                    
                    if (finished) break;

                    try {
                        // Pausa para evitar consumir CPU excesivamente
                        Thread.sleep(500); 
                    } catch (InterruptedException e) {
                        log.debug("DCC Polling: Hilo interrumpido. Saliendo.");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("DCC Polling: Excepción inesperada durante el polling para {}: {}", targetNick, e.getMessage(), e);
                        Platform.runLater(() -> mainController.appendSystemMessage("⚠️ Error interno de DCC al enviar archivo (polling fallido)."));
                        break;
                    }
                }
                
                // Asegurar notificación final al 100% solo si se completó con éxito
                if (transfer.getProgress() >= total) {
                    log.debug("DCC Polling: Notificación final 100% para la UI.");
                     consumer.accept(total, total);
                }
               
                // Limpiar el callback al finalizar el bucle
                dccProgressConsumers.remove(targetNick.toLowerCase());
                log.debug("DCC Polling: Callback de progreso eliminado para {}", targetNick);
                
            }).start();
        } else {
            log.warn("DCC Send: No se encontró callback de progreso para {}. La transferencia se enviará, pero sin UI de progreso.", targetNick);
        }
    }
        
    // ==========================================================
    // MÉTODOS DE DCC HANDLER - PIRC BOT OVERRIDES (Lado Receptor)
    // ==========================================================

    /**
     * Manejo del lado RECEPTOR (onIncomingFileTransfer).
     */
 
 
 // Clase ChatBot.java (RECEPTOR) - SIGUIENDO EL JAVADOC
 // En ChatBot.java

    @Override
    protected void onIncomingFileTransfer(DccFileTransfer transfer) {
        
        // El logger muestra que la solicitud ha llegado.
        log.info("🔔 Solicitud DCC entrante de: {} para el archivo: {}", 
                 transfer.getNick(), transfer.getFile().getName());

        // ⭐ ¡CAMBIO CRÍTICO!
        // No aceptamos el archivo aquí. En su lugar, delegamos la decisión al ChatController
        // para que muestre el popup de aceptación/denegación en la UI.
        
        Platform.runLater(() -> {
            // Asumiendo que mainController es de tipo ChatController
            // y que tiene el nuevo método para mostrar la ventana de transferencia.
            if (mainController != null) {
            	mainController.handleIncomingDccRequest(NICKNAME, transfer);
             
            } else {
                // Si el controlador principal no está listo, denegar para evitar cuelgues
                log.warn("MainController no disponible. Denegando transferencia automáticamente.");
                transfer.close(); 
            }
        });
        
        // Importante: El hilo de PircBot termina aquí. La acción (aceptar o denegar)
        // se realiza más tarde cuando el usuario interactúe con el popup de JavaFX.
    }
    /**
     * Evento de fin de transferencia (onFileTransferFinished).
     */
    @Override
    protected void onFileTransferFinished(DccFileTransfer transfer, Exception e) {
        log.debug("DCC Finish Event: Transferencia con {} ha finalizado.", transfer.getNick());
        if (e != null) {
            log.error("❌ Transferencia DCC fallida con {}. Causa: {}", transfer.getNick(), e.getMessage(), e);
            Platform.runLater(() -> mainController.appendSystemMessage("❌ Transferencia DCC fallida con " + transfer.getNick() + "."));
        } else {
            log.info("✅ Transferencia DCC finalizada con éxito con {}. Bytes finales: {}", transfer.getNick(), transfer.getProgress());
        }
    }
        
      
    // ==========================================================
    // EVENTOS DE CONEXIÓN Y DESCONEXIÓN
    // ==========================================================

    @Override
    protected void onConnect() {
        log.debug("Event: onConnect disparado.");
        mainController.setConnected(true);
        log.info("✅ Conexión IRC establecida con {}", getServer());
        
        Platform.runLater(() -> {
            mainController.getInputField().setDisable(false);
            mainController.appendSystemMessage("✅ Conectado al servidor: " + getServer());
        });

        // Identificación con NickServ
        String password = mainController.getPassword();
        if (password != null && !password.isEmpty()) {
            this.sendMessage("NickServ", "IDENTIFY " + (password.length() > 5 ? password.substring(0, 5) + "..." : password));
            log.debug("🔐 Enviando comando IDENTIFY a NickServ.");
        }

        // Unirse a canales automáticamente (después de una pequeña pausa)
        new Thread(() -> {
            try {
                Thread.sleep(2000); 
                String[] canales = {"#tester", "#chat"};
                log.debug("Iniciando rutina de auto-join a canales.");
                for (String canal : canales) {
                    this.joinChannel(canal);
                    log.debug("🔹 Enviado JOIN para canal: {}", canal);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⏹️ Hilo de unión a canales interrumpido");
            }
        }).start();
    }
    
    @Override
    protected void onDisconnect() {
        log.debug("Event: onDisconnect disparado.");
        String server = getServer();
        String nick = getNick();
        
        log.warn("🔴 {} desconectado de {}", nick, server);
        
        Platform.runLater(() -> {
            mainController.appendSystemMessage("🔴 " + nick + " desconectado de " + server);
            mainController.getInputField().setDisable(true);
            mainController.setConnected(false);
        });
    }

    // ==========================================================
    // EVENTOS DE MENSAJE Y NOTIFICACIÓN
    // ==========================================================

    @Override
    protected void onMessage(String channel, String sender, String login, String hostname, String message) {
        // Es buena práctica normalizar el canal a minúsculas, aunque PircBot ya lo hace en muchos casos.
        final String lowercaseChannel = channel.toLowerCase();
        
        log.debug("Event: onMessage. Canal: {}, Remitente: {}", channel, sender);

        // 1. Usa el MainController para obtener el wrapper de la ventana abierta.
        // Asumimos que mainController.getCanalesAbiertos() devuelve Map<String, CanalVentana>.
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(lowercaseChannel);

        if (ventanaWrapper != null) {
            // 2. Si la ventana existe, usamos Platform.runLater para actualizar la UI.
            Platform.runLater(() -> {
                // Llama al método appendMessage del CanalController asociado a esa ventana.
                ventanaWrapper.controller.appendMessage(sender, message);
            });
            
            log.info("Mensaje en {}: <{}> {}", channel, sender, message.length() > 50 ? message.substring(0, 50) + "..." : message);
        } 
        // Si ventanaWrapper es null, la ventana está cerrada, y el mensaje se ignora (comportamiento típico).
    }
    
    @Override
    protected void onPrivateMessage(String sender, String login, String hostname, String message) {
        log.debug("Event: onPrivateMessage. Remitente: {}", sender);
        // PircBot solo dispara esto para mensajes de texto (no DCC).
        Platform.runLater(() -> mainController.onPrivateMessageRemoto(sender, message));
    }

    @Override
    protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String message) {
        log.debug("Event: onNotice. Fuente: {}, Objetivo: {}", sourceNick, target);
        String source = sourceNick != null ? sourceNick : getServer();

        log.info("[NOTICE de {}] {}", source, message);

        // Lógica Anti-Bot
        if (message.contains("sum") || message.contains("calcula") || message.contains("resultado de") || message.contains("what is")) {
            log.warn("⚠️ Mensaje Anti-Bot (NOTICE) detectado: {}", message);
            String respuesta = parseAndSolveBotQuiz(message);
            
            if (respuesta != null) {
                log.debug("Lógica Anti-Bot: Respuesta calculada: {}", respuesta);
                Platform.runLater(() -> mainController.appendSystemMessage("🤖 Intentando responder Anti-Bot (NOTICE) con: " + respuesta));
                this.sendRawLine(respuesta); 
                log.info("✅ Respuesta Anti-Bot enviada: {}", respuesta);
                return;
            }
        }

        // Mostrar NOTICE como mensaje del sistema
        Platform.runLater(() -> {
            mainController.appendSystemMessage("[NOTICE de " + source + "] " + message);
        });
    }

    // ==========================================================
    // EVENTOS DE CANAL Y USUARIO
    // ==========================================================

    @Override
    protected void onJoin(String channel, String sender, String login, String hostname) {
        // Normalizar el nombre del canal para asegurar la consistencia con las claves del mapa.
        final String lowercaseChannel = channel.toLowerCase();
        log.debug("Event: onJoin. Canal: {}, Usuario: {}", channel, sender);

        // Obtener el envoltorio de la ventana (solo si está abierta)
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(lowercaseChannel);

        // ----------------------------------------------------------------------------------
        // Lógica para cuando TU PROPIO BOT se une al canal (sender == getNick())
        // ----------------------------------------------------------------------------------
        if (sender.equalsIgnoreCase(getNick())) {
            log.debug("Propio JOIN: Notificando entrada, registrando unión y solicitando NAMES.");
            
            // 1. Registrar el estado de unión (asumiendo que 'joinedChannels' existe en ChatBot)
            // Esto es vital para saber si el bot está realmente en el canal.
            joinedChannels.add(lowercaseChannel); 
            
            // 2. Notificar al usuario principal y forzar la aparición en la lista.
            mainController.appendSystemMessage("🚪 Has entrado al canal " + channel + ".");
            
            Platform.runLater(() -> {
                // Fuerza la adición del canal a la lista visible de canales DISPONIBLES (TableView).
                mainController.forzarCanalUnidoEnLista(channel);
            });

            // 3. ⭐ CRÍTICO: Solicitar la lista de usuarios inmediatamente.
            // Esto asegura que, si la ventana se abre más tarde, los datos NAMES están disponibles.
            this.sendRawLine("NAMES " + channel); 
            log.debug("Enviado NAMES después del JOIN del propio bot en {}", channel);
        } 
        
        // ----------------------------------------------------------------------------------
        // Lógica para OTROS usuarios que se unen (actualiza lista y contador)
        // ----------------------------------------------------------------------------------
        else if (ventanaWrapper != null) {
            // Esta lógica solo se ejecuta si la ventana fue abierta previamente.
            Platform.runLater(() -> {
                // 1. Notificar la unión en la ventana de chat
                ventanaWrapper.controller.appendSystemMessage(
                    "» " + sender + " se ha unido a " + channel, 
                    CanalController.MessageType.JOIN, 
                    sender
                );
                
                // 2. ⭐ CRÍTICO: Agregar el nuevo usuario a la lista local y actualizar el contador.
                // Necesitas que 'addUserToList(String)' esté implementado en CanalController.
                ventanaWrapper.controller.addUserToList(sender);
                
                log.debug("Usuario {} añadido localmente después de JOIN en {}", sender, channel);
            });
        }
    }
    @Override
    protected void onUserList(String channel, org.jibble.pircbot.User[] users) {
        log.debug("Event: onUserList recibido para {}. Total de usuarios: {}", channel, users.length);
        // 1. Convertir el array de User a una lista de Strings con el prefijo
        List<String> userNicks = new ArrayList<>();
        for (org.jibble.pircbot.User user : users) {
        	userNicks.add(user.getPrefix() + user.getNick());
        }
        
        // 2. Ejecutar la actualización en el hilo de JavaFX
        Platform.runLater(() -> {
            mainController.actualizarUsuariosCanal(channel, userNicks);
        });
        
        log.debug("Lista de usuarios actualizada para {}", channel);
        log.info("Lista de usuarios recibida para {}. Total: {}", channel, userNicks.size());
    }

    @Override
    protected void onPart(String channel, String sender, String login, String hostname) {
        final String lowercaseChannel = channel.toLowerCase();
        log.debug("Event: onPart. Canal: {}, Usuario: {}", channel, sender);
        
        // Obtener la ventana si está abierta
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(channel);
        
        // Lógica para cuando TU PROPIO BOT sale (sender.equals(getNick()))
        if (sender.equalsIgnoreCase(getNick())) {
            log.debug("Propio PART: Cerrando ventana de canal y eliminando de rastreador.");
            
            // ⭐ 1. ACTUALIZAR ESTADO: Eliminar el canal del rastreador de uniones.
            // Asume que tienes el Set<String> joinedChannels en ChatBot.
            joinedChannels.remove(lowercaseChannel); 

            // 2. Cerrar la ventana y notificar
            mainController.cerrarCanalDesdeVentana(channel);
            mainController.appendSystemMessage("« Has salido del canal " + channel);
            
        // Lógica para cuando otro usuario sale (¡EFICIENTE!)
        } else if (ventanaWrapper != null) {
            // Ejecutar en el hilo de JavaFX
            Platform.runLater(() -> {
                String mensaje = "« " + sender + " ha salido de " + channel;
                
                // 1. Mostrar mensaje de salida en el chat
                ventanaWrapper.controller.appendSystemMessage(
                    mensaje, 
                    CanalController.MessageType.PART, 
                    sender
                );
                
                // ⭐ 2. CORRECCIÓN CLAVE: Notificar al controlador que elimine al usuario
                // Necesitas implementar este nuevo método en CanalController.
                ventanaWrapper.controller.removeUserFromList(sender);
                
                log.debug("Notificando remoción de usuario {} después de PART en {}", sender, channel);
            });
        }
    }

    @Override
    protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
        log.debug("Event: onQuit. Usuario: {}, Razón: {}", sourceNick, reason);
        Platform.runLater(() -> {
            mainController.appendSystemMessage("« " + sourceNick + " ha abandonado IRC (" + reason + ")");
            // Actualizar listas de usuarios
            for (String canal : mainController.getCanalesAbiertos().keySet()) {
                this.sendRawLine("NAMES " + canal); 
                log.debug("Enviado NAMES para actualizar lista después de QUIT en {}", canal);
            }
        });
    }

    @Override
    protected void onNickChange(String oldNick, String login, String hostname, String newNick) {
        log.debug("Event: onNickChange. De {} a {}", oldNick, newNick);
        Platform.runLater(() -> {
            mainController.appendSystemMessage("↔️ " + oldNick + " ahora es conocido como " + newNick);
            
            // Actualizar listas de usuarios en todos los canales
            for (String canal : mainController.getCanalesAbiertos().keySet()) {
                this.sendRawLine("NAMES " + canal); 
                log.debug("Enviado NAMES para actualizar lista después de nick change en {}", canal);
            }
        });
    }

    
    
    // --- SOBREESCRITURA PARA PROCESAR LA RESPUESTA LIST (322, 323) ---

 

    @Override
    protected void onServerResponse(int code, String response) {
        super.onServerResponse(code, response); 
        // Usamos el log estándar para la respuesta, fuera de la condición de listado
        log.debug("Event: onServerResponse. Código: {}, Respuesta: {}", code, response);


        if (!isListingChannels) return;

     
        if (code == 322) { // Respuesta LIST (RPL_LIST): Datos de un canal
            log.debug("Procesando respuesta 322 (Canal LIST).");
            
            System.out.println("------------------------------------------------------------------");
            System.out.println(">>> RESPUESTA 322 COMPLETA (ORIGINAL): " + response); 
            
            try {
                String channelName = "";
                int userCount = 0;
                String modos = "";
                String topic = "";
                
                final String BOT_NICK = getNick(); // Usa getNick() para asegurar el nick actual
                
                // 1. Aislar la sección de datos que contiene [Canal] [Usuarios] [Modos] :[Tema]
                int startOfDataIndex = response.indexOf(BOT_NICK);
                if (startOfDataIndex == -1) {
                    log.warn("Parseo 322 falló: No se pudo anclar al nick del bot.");
                    return;
                }
                
                // La sección de datos comienza después de 'nick '
                String dataPart = response.substring(startOfDataIndex + BOT_NICK.length()).trim();
                
                // 2. Dividir para obtener los primeros campos fijos
                // Limitamos a 4 para asegurarnos de que el resto (el topic) se queda unido
                String[] tokens = dataPart.split(" ", 4); 
                
                // La respuesta esperada debe tener al menos: [Canal] [Usuarios] [Topic]
                if (tokens.length < 3) {
                    log.warn("Parseo 322 falló: Pocos tokens después del nick. Data: {}", dataPart);
                    return;
                }

                // --- EXTRACCIÓN DE CAMPOS ---
                
                // 1. Nombre del Canal (tokens[0])
                channelName = tokens[0];
                
                // 2. Número de Usuarios (tokens[1])
                try {
                    userCount = Integer.parseInt(tokens[1]);
                } catch (NumberFormatException ignored) { }
                
                // 3. Tema/Descripción (siempre el último token disponible)
                String lastToken = tokens[tokens.length - 1];
                
                if (lastToken.startsWith(":")) {
                    topic = lastToken.substring(1).trim();
                } else {
                    topic = lastToken;
                }
                
                // 4. Modos/Permisos
                // Esto es lo más difícil: Puede ser tokens[2] o puede estar contenido en el tema.
                if (tokens.length == 4) {
                    // Si hay 4 tokens, tokens[2] es el modo.
                    modos = tokens[2];
                } else {
                    // Si hay 3 tokens (solo Canal, Usuarios, Tema), los modos están vacíos.
                    modos = "";
                }
                
                // *CASO ESPECIAL DE UNREALIRCd*
                // A veces, UnrealIRCd pone los modos en el tema (ej: :[+nt]).
                if (modos.isEmpty() && topic.startsWith("[+") && topic.endsWith("]")) {
                    modos = topic.substring(1, topic.length() - 1);
                    topic = ""; // Si el tema solo era el modo, lo vaciamos.
                }

                // *LIMPIEZA FINAL*
                if (!modos.isEmpty() && !modos.startsWith("+")) {
                     modos = "+" + modos;
                }
                
                // ⭐⭐⭐ IMPRESIÓN DE TOKENS PARSEADOS ⭐⭐⭐
                System.out.println("PARSING EXITOSO (FINAL):");
                System.out.println("  Canal: " + channelName);
                System.out.println("  Usuarios: " + userCount);
                System.out.println("  Modos: " + modos);
                System.out.println("  Descripción (Topic): " + (topic.length() > 60 ? topic.substring(0, 60) + "..." : topic)); 
                System.out.println("------------------------------------------------------------------");
                log.debug("Parseo 322: Canal={}, Usuarios={}, Modos={}, Topic={}", channelName, userCount, modos, topic);

                // 5. Crear y enviar el objeto Canal
                Canal canal = new Canal(channelName, userCount, modos, topic);
                
                if (currentListReceiver != null && channelName.startsWith("#")) {
                    currentListReceiver.accept(canal);
                    log.debug("Parseo 322: Enviando objeto Canal al receptor.");
                }
                
            } catch (Exception e) {
                System.err.println("Error FATAL al parsear respuesta 322: " + response + " (" + e.getMessage() + ")");
                e.printStackTrace(); 
                log.error("Error FATAL al parsear respuesta 322.", e);
            }
        }  else if (code == 323) { // Fin de lista
            log.debug("Procesando respuesta 323 (Fin de Canal LIST).");
            this.isListingChannels = false;
            if (currentListEndCallback != null) {
                currentListEndCallback.run();
                log.debug("Ejecutando callback de fin de lista.");
            }
            this.currentListReceiver = null;
            this.currentListEndCallback = null;
            
            System.out.println("⭐ Fin de la lista de canales (323).");
        }
        
        if (code == 353 || code == 366) {
            // La respuesta es típicamente: :server 353 nick = #canal :users...
            String[] parts = response.split(" ");
            String channel = null;

            // Intentar extraer el nombre del canal de la respuesta
            if (code == 353 && parts.length >= 5) {
                // El canal está en el índice 4 de la respuesta 353
                channel = parts[4]; 
            } else if (code == 366 && parts.length >= 4) {
                 // El canal está en el índice 3 de la respuesta 366
                 channel = parts[3];
            }

            if (channel != null && namesDelegates.containsKey(channel)) {
                CanalController delegate = namesDelegates.get(channel);
                if (delegate != null) {
                    Platform.runLater(() -> {
                        // Delegamos el manejo de la respuesta directamente al controlador del canal
                        delegate.handleNamesResponse(code, response); 
                    });
                }
            }
        }
    }
    
    
    @Override
    protected void onAction(String sender, String login, String hostname, String target, String action) {
        log.debug("Event: onAction (/me). Remitente: {}, Objetivo: {}", sender, target);
        // Manejo de /me (CTCP ACTION)
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(target); 
        if (ventanaWrapper != null) {
            Platform.runLater(() -> {
                ventanaWrapper.controller.appendMessage(sender, action);
            });
        }
    }
    
    // ==========================================================
    // MÉTODOS AUXILIARES
    // ==========================================================

    private String parseAndSolveBotQuiz(String quizMessage) {
        try {
            log.debug("Aux: Intentando resolver quiz: {}", quizMessage);
            Pattern pattern = Pattern.compile("(\\d+)\\s*([+\\-*/])\\s*(\\d+)");
            Matcher matcher = pattern.matcher(quizMessage);

            if (matcher.find()) {
                long num1 = Long.parseLong(matcher.group(1));
                String operator = matcher.group(2);
                long num2 = Long.parseLong(matcher.group(3));
                long result = 0;
                log.debug("Aux: Quiz detectado: {} {} {}", num1, operator, num2);

                switch (operator) {
                    case "+": result = num1 + num2; break;
                    case "-": result = num1 - num2; break;
                    case "*": result = num1 * num2; break;
                    case "/": 
                        if (num2 != 0) result = num1 / num2; 
                        else {
                            log.warn("Aux: División por cero detectada.");
                            return null;
                        }
                        break;
                    default: return null;
                }
                log.debug("Aux: Resultado del quiz: {}", result);
                return String.valueOf(result); 
            }
        } catch (Exception e) {
            log.error("❌ Error al intentar resolver el quiz anti-bot: {}", quizMessage, e);
        }
        return null;
    }
    
    /**
     * Inicia la solicitud LIST y registra los callbacks para que CanalesListController
     * reciba los datos a medida que llegan.
     */
    public void requestChannelList(Consumer<Canal> receiver, Runnable endCallback) {
        log.debug("Channel List: Solicitud LIST iniciada.");
        if (this.isConnected()) {
            this.currentListReceiver = receiver;
            this.currentListEndCallback = endCallback;
            this.isListingChannels = true;
            
            this.listChannels(); 
            log.debug("Channel List: Comando LIST enviado al servidor.");
        } else {
            log.warn("Channel List: No conectado. No se puede solicitar la lista de canales.");
        }
    }
}