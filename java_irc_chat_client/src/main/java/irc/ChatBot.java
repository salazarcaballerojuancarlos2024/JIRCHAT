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
import java_irc_chat_client.IRCUser;
import java_irc_chat_client.LWhoController;
import javafx.application.Platform;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.io.IOException;
import org.slf4j.Logger;


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
    private static final java.util.Set<String> CANALES_AUTO_JOIN_EXCLUIDOS = java.util.Set.of("#chat", "#tester");
    
    // ⭐ El Nickname debe coincidir con el utilizado en onServerResponse para el parseo
    //private static final String NICKNAME = "akkiles4321";
    private static final String NICKNAME = "Sakkiles4321";
    
    // ⭐ DCC: Mapa para guardar el callback de progreso para CADA NICK (Lado Emisor)
    private final Map<String, BiConsumer<Long, Long>> dccProgressConsumers = new HashMap<>();
    
    private final Map<String, CanalController> namesDelegates = new ConcurrentHashMap<>();
    
    // Referencia al controlador que está esperando la respuesta WHO. 
    // Usaremos un mapa simple si permitimos múltiples consultas WHO,
    // pero por simplicidad, usaremos una sola referencia activa.
 private LWhoController activeLWhoController; 

 // Método para que LWhoController se registre y envíe la consulta
 public void requestWhoList(String channel, LWhoController controller) {
     if (!isConnected()) return;
     this.activeLWhoController = controller; // Guardamos la referencia para callbacks
     sendRawLine("WHO " + channel);
 }
    
    
    
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

 // Dentro de ChatBot.java

    @Override
    protected void onConnect() {
        log.debug("Event: onConnect disparado.");
        mainController.setConnected(true);
        log.info("✅ Conexión IRC establecida con {}", getServer());
        
        // 1. Actualizar la UI inmediatamente
        Platform.runLater(() -> {
            mainController.getInputField().setDisable(false);
            mainController.appendSystemMessage("✅ Conectado al servidor: " + getServer());
        });

        // 2. Rutina de comandos y auto-join en un hilo separado
        new Thread(() -> {
            try {
                // Pausa inicial para permitir que el servidor termine de procesar el registro (NICK/USER)
                Thread.sleep(1000); 

                // 2a. Identificación con NickServ
                String password = mainController.getPassword();
                if (password != null && !password.isEmpty()) {
                    // Siempre envía el comando NickServ primero
                    this.sendMessage("NickServ", "IDENTIFY " + (password.length() > 5 ? password.substring(0, 5) + "..." : password));
                    log.debug("🔐 Enviando comando IDENTIFY a NickServ.");
                    
                    // Pausa adicional para que NickServ responda (es buena práctica)
                    Thread.sleep(1500); 
                }

                // 2b. Ejecutar la Secuencia de Inicio (si está marcada)
                // Asumiendo que ChatController tiene el getter y el método ejecutarSecuenciaInicio(boolean)
                if (mainController.isSecuenciaInicioActivada()) {
                    // El método ejecutarSecuenciaInicio() dentro de ChatController se encarga de leer el archivo
                    // y usar bot.sendRawLine() para enviar los comandos al servidor.
                    mainController.ejecutarSecuenciaInicio(true);
                    
                    // Pausa para que se ejecuten los comandos de la secuencia (como /join si están ahí)
                    Thread.sleep(1000);
                }

                // 2c. Unión a canales automáticos (solo si no se hizo ya en la secuencia de inicio)
                // Si la secuencia de inicio ya maneja los JOINs, puedes comentar o eliminar esta sección.
                // Si quieres asegurarte de que #tester y #chat se unan siempre:
                String[] canales = {"#tester", "#chat"};
                log.debug("Iniciando rutina de auto-join a canales predeterminados.");
                for (String canal : canales) {
                    // Nota: Usamos joinChannel() de PircBot, que maneja la sintaxis correcta.
                    this.joinChannel(canal);
                    log.debug("🔹 Enviado JOIN para canal: {}", canal);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⏹️ Hilo de rutina de inicio interrumpido");
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
            
            // ⭐ CÓDIGO CRÍTICO AÑADIDO:
            // El ChatController debe anular la referencia al objeto ChatBot que acaba de morir.
            mainController.setBot(null); // Asumiendo que tienes un setter llamado setBot.
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
            log.debug("Propio JOIN: Registrando unión y solicitando NAMES (sin afectar UI).");
            
            // 1. Registrar el estado de unión
            // Esto es CLAVE: permite a ChatController saber que el bot está unido.
            joinedChannels.add(lowercaseChannel); 
            
            Platform.runLater(() -> {
                // ⭐ IMPORTANTE: NO se llama a mainController.agregarCanalAbierto(channel).
                // Esto evita que los canales de autounión (#chat, #tester, etc.) abran su ventana y creen el botón al inicio.
                
                // Si el usuario ya había abierto la ventana (ej. por /join que llama a agregarCanalAbierto antes), 
                // no pasa nada, la ventana ya existe y tiene su botón.

                // 2. Notificar al usuario principal (solo en la consola de estado)
                mainController.appendSystemMessage("🚪 Has entrado al canal " + channel + ".");
                
                // 3. Forzar la adición del canal a la lista visible de canales disponibles (TableView), si aplica.
                mainController.forzarCanalUnidoEnLista(channel);
            });

            // 4. Solicitar la lista de usuarios inmediatamente.
            this.sendRawLine("NAMES " + channel); 
            log.debug("Enviado NAMES después del JOIN del propio bot en {}", channel);
        } 
        
        // ----------------------------------------------------------------------------------
        // Lógica para OTROS usuarios que se unen (actualiza lista y contador)
        // ----------------------------------------------------------------------------------
        else if (ventanaWrapper != null) {
            // Esta lógica solo se ejecuta si la ventana fue abierta previamente por el usuario.
            Platform.runLater(() -> {
                // 1. Notificar la unión en la ventana de chat
                ventanaWrapper.controller.appendSystemMessage(
                    "» " + sender + " se ha unido a " + channel, 
                    CanalController.MessageType.JOIN, 
                    sender
                );
                
                // 2. Agregar el nuevo usuario a la lista local y actualizar el contador.
                // Asegúrate de que 'addUserToList(String)' esté implementado en CanalController.
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
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(lowercaseChannel);
        
        // Lógica para cuando TU PROPIO BOT sale (sender.equals(getNick()))
        if (sender.equalsIgnoreCase(getNick())) {
            log.debug("Propio PART: Cerrando ventana de canal y eliminando de rastreador.");
            
            // ⭐ NUEVA LÍNEA: 1. Remover el botón del leftPane AHORA.
            // Asumiendo que el método removerBotonCanal está en ChatController.
            mainController.removerBotonCanal(channel); 
            
            // 2. ACTUALIZAR ESTADO: Eliminar el canal del rastreador de uniones.
            joinedChannels.remove(lowercaseChannel); 

            // 3. Cerrar la ventana y notificar
            // Nota: cerrarCanalDesdeVentana solo elimina la referencia del mapa y cierra la Stage.
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
                
                // 2. Notificar al controlador que elimine al usuario
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

 

 // Dentro de ChatBot.java

 // Dentro de ChatBot.java

    @Override
    protected void onServerResponse(int code, String response) {
        super.onServerResponse(code, response);

        // Usamos el log estándar para la respuesta
        log.debug("Event: onServerResponse. Código: {}, Respuesta: {}", code, response);

        // ----------------------------------------------------------------------
        //             MANEJO DE LISTADO DE CANALES (322, 323)
        // ----------------------------------------------------------------------

        if (isListingChannels) {
            if (code == 322) { // Respuesta LIST (RPL_LIST): Datos de un canal
                log.debug("Procesando respuesta 322 (Canal LIST).");

                try {
                    // ... (Lógica de parseo 322 existente, se mantiene sin cambios) ...

                    String channelName = "";
                    int userCount = 0;
                    String modos = "";
                    String topic = "";

                    final String BOT_NICK = getNick();
                    int startOfDataIndex = response.indexOf(BOT_NICK);
                    if (startOfDataIndex == -1) {
                        log.warn("Parseo 322 falló: No se pudo anclar al nick del bot.");
                        return;
                    }

                    String dataPart = response.substring(startOfDataIndex + BOT_NICK.length()).trim();
                    String[] tokens = dataPart.split(" ", 4);

                    if (tokens.length < 3) {
                        log.warn("Parseo 322 falló: Pocos tokens después del nick. Data: {}", dataPart);
                        return;
                    }

                    channelName = tokens[0];
                    try {
                        userCount = Integer.parseInt(tokens[1]);
                    } catch (NumberFormatException ignored) { }

                    String lastToken = tokens[tokens.length - 1];

                    if (lastToken.startsWith(":")) {
                        topic = lastToken.substring(1).trim();
                    } else {
                        topic = lastToken;
                    }

                    if (tokens.length == 4) {
                        modos = tokens[2];
                    } else {
                        modos = "";
                    }

                    // Caso especial de UnrealIRCd para modos en el tema
                    if (modos.isEmpty() && topic.startsWith("[+") && topic.endsWith("]")) {
                        modos = topic.substring(1, topic.length() - 1);
                        topic = "";
                    }

                    if (!modos.isEmpty() && !modos.startsWith("+")) {
                         modos = "+" + modos;
                    }

                    log.debug("Parseo 322: Canal={}, Usuarios={}, Modos={}, Topic={}", channelName, userCount, modos, topic);

                    Canal canal = new Canal(channelName, userCount, modos, topic);

                    // ⭐ CLAVE: Platform.runLater para enviar datos al receptor (UI)
                    if (currentListReceiver != null && channelName.startsWith("#")) {
                        Platform.runLater(() -> {
                            currentListReceiver.accept(canal);
                            log.debug("Parseo 322: Enviando objeto Canal al receptor.");
                        });
                    }

                } catch (Exception e) {
                    log.error("Error FATAL al parsear respuesta 322: {}", response, e);
                }
                return; // Salir después de procesar LIST
            }  else if (code == 323) { // Fin de lista
                log.debug("Procesando respuesta 323 (Fin de Canal LIST).");
                this.isListingChannels = false;
                
                // Capturamos las referencias locales antes de la limpieza final
                final Runnable endCallback = this.currentListEndCallback;
                
                // ⭐ CLAVE: Envolvemos el fin de consulta y la limpieza DENTRO del Platform.runLater.
                // Esto garantiza que la limpieza (poniendo a null) se haga en el hilo FX,
                // *después* de que todos los 322 pendientes en la cola hayan tenido la oportunidad de ejecutarse.
                Platform.runLater(() -> {
                    if (endCallback != null) {
                        endCallback.run();
                        log.debug("Ejecutando callback de fin de lista en hilo FX.");
                    }
                    
                    // ⭐⭐ CORRECCIÓN: Limpieza final dentro del hilo FX ⭐⭐
                    this.currentListReceiver = null;
                    this.currentListEndCallback = null;
                });

                return; // Salir después de procesar LIST
            }
        }

        // ----------------------------------------------------------------------
        //               MANEJO DE CONSULTA WHO (352, 315)
        // ----------------------------------------------------------------------

        if (activeLWhoController != null) {
            if (code == 352) {
                log.debug("Procesando respuesta 352 (WHO Reply).");
                try {

                    String[] tokens = response.split(" ");

                    if (tokens.length < 7) {
                        log.warn("Parseo 352 falló: Pocos tokens para WHO reply. Longitud: {}", tokens.length);
                        return;
                    }

                    // --- Mapeo de Índices (Basado en tu última salida RAW) ---
                    // [0] El_ArWen, [1] #canal, [2] ident, [3] host, [4] server, [5] nick, [6] flags...

                    String username = tokens[2];         // androirc
                    String hostname = tokens[3];         // i-7iq.48u.crjpc4.IP
                    String server   = tokens[4];         // melocoton.chatzona.org
                    String nick     = tokens[5];         // AAngelica_ch
                    String flags    = tokens[6];         // H@r

                    // 1. Encontrar el inicio del Real Name (después del primer ':')
                    int realNameStartIndex = response.indexOf(":");
                    String realNamePart = "";
                    
                    if (realNameStartIndex != -1) {
                        // La parte raw es típicamente ":0 Amarte.es.mi.mejor.decisión."
                        String rawRealName = response.substring(realNameStartIndex + 1).trim();
                        
                        // 2. ⭐ CORRECCIÓN: Eliminar el '0' del hopcount si está presente
                        if (rawRealName.startsWith("0 ")) {
                            realNamePart = rawRealName.substring(2).trim(); // Elimina "0 "
                        } else {
                            realNamePart = rawRealName;
                        }
                    } 
                    // Ahora realNamePart es: "Amarte.es.mi.mejor.decisión." (Limpio)

                    // ⭐ CREACIÓN DEL OBJETO IRCUser con el mapeo correcto
                    // Constructor: IRCUser(nick, userHost, flags, server, realName)
                    IRCUser user = new IRCUser(
                        nick,
                        username + "@" + hostname,
                        flags,
                        server,
                        realNamePart
                    );

                    // ⭐ CLAVE: Solución de Hilo FX
                    Platform.runLater(() -> {
                        activeLWhoController.receiveUser(user);
                    });

                } catch (Exception e) {
                    log.error("Error FATAL al parsear respuesta 352. Respuesta completa: {}", response, e);
                }
                return;
            }
         else if (code == 315) {
             log.debug("Procesando respuesta 315 (Fin de WHO).");

             String[] tokens = response.split(" ");
             // Después de "El_ArWen ", el canal es el segundo token si la respuesta no empieza con el nick del bot
             String channel = tokens.length >= 2 ? tokens[1] : (tokens.length >= 1 ? tokens[0] : "N/A"); // Mayor seguridad
             
             // ⭐ CLAVE: Solución de Hilo FX
             Platform.runLater(() -> {
                 activeLWhoController.finishQuery(channel);
                 this.activeLWhoController = null; // Limpiar la referencia activa
             });
             return;
         }
        }


        // ----------------------------------------------------------------------
        //                MANEJO DE NAMES (353, 366)
        // ----------------------------------------------------------------------

        if (code == 353 || code == 366) {
            String[] parts = response.split(" ");
            String channel = null;

            if (code == 353 && parts.length >= 5) {
                channel = parts[4];
            } else if (code == 366 && parts.length >= 4) {
                 channel = parts[3];
            }

            if (channel != null && namesDelegates.containsKey(channel)) {
                CanalController delegate = namesDelegates.get(channel);
                if (delegate != null) {
                    // ⭐ CLAVE: Solución de Hilo FX
                    Platform.runLater(() -> {
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