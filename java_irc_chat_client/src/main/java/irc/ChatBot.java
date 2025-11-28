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
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
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
import java_irc_chat_client.ChatController;


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
    private static final String NICKNAME = "akkiles4321";
    //private static final String NICKNAME = "Sakkiles4321";
    
 // Contador para llevar la cuenta de cuántos canales procesamos con JOIN/PART
    private int channelsTestedCount = 0;
    
    // ⭐ DCC: Mapa para guardar el callback de progreso para CADA NICK (Lado Emisor)
    private final Map<String, BiConsumer<Long, Long>> dccProgressConsumers = new HashMap<>();
    
    private final Map<String, CanalController> namesDelegates = new ConcurrentHashMap<>();
    
    // Referencia al controlador que está esperando la respuesta WHO. 
    // Usaremos un mapa simple si permitimos múltiples consultas WHO,
    // pero por simplicidad, usaremos una sola referencia activa.
    private LWhoController activeLWhoController; 
 
    private ChatController chatController;

 // Dentro de ChatBot.java

 // ⭐ 1. Lista global de todos los nicks conectados al servidor. 
 // Mantenida y actualizada por los eventos JOIN/QUIT.
 private final Set<String> connectedNicks = new HashSet<>();

 // ⭐ 2. Cola para la sincronización inicial: canales a unirse temporalmente.
 private final Queue<String> channelsToSync = new LinkedList<>();

 // ⭐ 3. Bandera para controlar la fase de sincronización inicial (JOIN/PART secuencial).
 private boolean isSyncingChannel = false;
 
//⭐ CONSTRUCTOR REQUERIDO ⭐
 public ChatBot(ChatController controller, String nick, String login) {
     // Llama al constructor de PircBot (si usas PircBot)
     // El nick debe establecerse ANTES de conectar
     this.setName(nick); 
     this.setLogin(login); // O setIdent si usas un nombre de usuario diferente
     
     // Almacenar la referencia al controlador principal para callbacks
     this.mainController = controller; 
     
     // Opcional: Configuración inicial del bot (ej. logger)
     this.setVerbose(true); 
 }
/**
* Consulta la lista global de nicks conectados en memoria.
* @param nick El nick del usuario a comprobar.
* @return True si el nick está actualmente conectado al servidor.
*/
public boolean isNickConnected(String nick) {
  // Usamos toLowerCase() para una comprobación insensible a mayúsculas/minúsculas, 
  // común en IRC.
  return connectedNicks.contains(nick.toLowerCase()); 
}

/**
* Verifica si el nick dado está actualmente conectado al servidor (presente en AL MENOS
* uno de los canales a los que el bot está unido).
* @param nick El nick a comprobar.
* @return true si el nick está presente en cualquier canal del bot.
*/
public boolean isNickOnServer(String nick) {
  if (nick == null || nick.isEmpty()) {
      return false;
  }
  final String targetNick = nick.toLowerCase();
  
  // 1. Obtener la lista de canales a los que estamos unidos
  String[] channels = this.getChannels(); 
  
  for (String channel : channels) {
      // 2. Obtener la lista de usuarios para el canal actual
      User[] users = this.getUsers(channel); 
      
      // 3. Iterar y buscar el nick
      for (User user : users) {
          // PircBot maneja la comparación de nicks, pero es más seguro usar lowerCase
          if (user.getNick().toLowerCase().equals(targetNick)) {
              return true; // ¡Nick encontrado!
          }
      }
  }
  
  return false; // Nick no encontrado en ningún canal
}

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
        
 
    @Override
    protected void onChannelInfo(String channel, int userCount, String topic) {
        super.onChannelInfo(channel, userCount, topic);
        
        // ⭐ Lógica de filtrado: Solo canales públicos que empiezan por '#' y tienen al menos un usuario ⭐
        if (channel != null && channel.startsWith("#") && userCount > 0) {
            
            // 1. Añadimos el canal a la cola de sincronización.
            // Usamos 'add' o 'offer' para añadir al final de la Queue.
            if (channelsToSync.add(channel)) { // El método add() de Collection retorna true si el elemento fue añadido.
                log.debug("Canal '{}' ({} usuarios) añadido a la cola de sincronización.", channel, userCount);
            }
            
        } else {
            log.debug("Canal '{}' ignorado (usuarios: {}, prefijo: {}).", channel, userCount, channel.startsWith("#") ? "OK" : "Error");
        }
    }
      


    /**
     * 📢 Inicia la fase de sincronización pidiendo al servidor la lista de canales.
     * La respuesta (onChannelInfo y onServerResponse(323)) gestiona los siguientes pasos.
     */
    public void startChannelListAndSync() {
        if (isSyncingChannel) return; // Evitar llamadas dobles.
        
        // Limpiar la cola de sincronización de un intento anterior
        channelsToSync.clear(); 
        
        // PircBot enviará el comando LIST al servidor.
        // Las respuestas serán capturadas por onChannelInfo() y onServerResponse(323).
        listChannels(); 
    }

 // ==========================================================
 // EVENTOS DE CONEXIÓN Y DESCONEXIÓN
 // ==========================================================

    @Override
    protected void onConnect() {
        final ChatController uiController = this.mainController; 

        // ⭐ 1. VERIFICACIÓN CRÍTICA: Impedir el NullPointerException
        if (uiController == null) {
            log.error("❌ FATAL: mainController (ChatController) no fue inicializado en el constructor.");
            return; 
        }
        
        // 2. Bloquear la UI y notificar (Debe ir en el hilo de JavaFX)
        Platform.runLater(() -> {
            // Deshabilitamos el input principal, pero el de verificación debe activarse más tarde (en el 001).
            uiController.getInputField().setDisable(true); 
            uiController.appendSystemMessage("✅ Conectado al servidor: " + getServer());
            
            // El mensaje de sincronización debe retrasarse, ya que aún no ha comenzado.
            // uiController.appendSystemMessage("🔄 Iniciando sincronización de usuarios globales. Espere..."); 
            
            // Mostrar mensaje de ESPERA DE VERIFICACIÓN
            uiController.appendSystemMessage("⚠️ Esperando mensaje de verificación Anti-Bot...");
            
            // [Añadir código para mostrar el indicador de progreso o pantalla de carga]
        });

        // 3. Rutina de comandos, JOINs y Sincronización en un hilo separado
        new Thread(() -> {
            try {
                // ⭐ Retraso para dar tiempo a que la conexión se estabilice
                Thread.sleep(1000); 

                // 3a. Identificación con NickServ
                String password = uiController.getPassword();
                if (password != null && !password.isEmpty()) {
                    this.sendMessage("NickServ", "IDENTIFY " + password);
                    log.debug("🔐 Enviando comando IDENTIFY a NickServ.");
                    Thread.sleep(1500); 
                }

                // 3b. Ejecutar la Secuencia de Inicio (Comandos raw personalizados)
                if (uiController.isSecuenciaInicioActivada()) {
                    uiController.ejecutarSecuenciaInicio(true);
                    Thread.sleep(1000);
                }

                // 3c. Unión a canales automáticos
                // ADVERTENCIA: Estos JOINs fallarán si la verificación Anti-Bot aún no se ha completado.
                String[] canales = {"#tester", "#chat"}; // O la lista dinámica que uses
                log.debug("Iniciando rutina de auto-join a canales predeterminados (puede fallar sin verificación).");
                for (String canal : canales) {
                    this.joinChannel(canal);
                    log.debug("🔹 Enviado JOIN para canal: {}", canal);
                    Thread.sleep(500); 
                }
                
                // ⭐ 4. ¡LÍNEA ELIMINADA!
                // ELIMINAMOS EL INICIO DE LA SINCRONIZACIÓN DE AQUÍ.
                // listChannels() AHORA SOLO SE LLAMA DESDE onNotice (VERIFICATION_DONE).
                // log.debug("📡 Iniciando fase de listado y sincronización de canales.");
                // startChannelListAndSync(); // <--- ¡ELIMINADO!
                
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

        // Siempre mostrar el mensaje en el log del sistema
        log.info("[NOTICE de {}] {}", source, message);
        
        // Bandera para saber si el NOTICE se manejó críticamente 
        boolean handledCritically = false;

        // ======================================================================
        // ⭐ 1. DETECCIÓN DEL AVISO DE VERIFICACIÓN (Habilita el input principal) ⭐
        // ======================================================================
        if (message.contains("Necesitas verificar que no eres un bot") || message.contains("valida tu conexión") || message.contains("URL") || message.contains("http")) { // Añadimos detección de URL/http
            
            log.warn("⚠️ Aviso de verificación Anti-Bot detectado. Mostrando URL/Código.");
            
            Platform.runLater(() -> {
                if (mainController != null) {
                    
                    // ⭐⭐ SOLUCIÓN AL PROBLEMA: Mostrar el mensaje ORIGINAL que contiene la URL/código. ⭐⭐
                    mainController.appendSystemMessage("--- ⚠️ MENSAJE DE VALIDACIÓN CRÍTICO ⚠️ ---");
                    mainController.appendSystemMessage("➡️ SERVER: " + message); 
                    mainController.appendSystemMessage("--- --------------------------------- ---");
                    
                    // Habilitamos el campo de texto principal para que el usuario pueda ingresar el código
                    mainController.syncFinished(); // Asumiendo que este método habilita el input
                    
                    mainController.appendSystemMessage("💬 Por favor, ¡COPIA y PEGA el código de validación AQUÍ o RESUELVE el QUIZ!.");
                }
            });
            handledCritically = true;
        }

        // ======================================================================
        // 2. Lógica de Respuesta a Quizzes Anti-Bot (Cálculos)
        // ======================================================================
        if (message.contains("sum") || message.contains("calcula") || message.contains("resultado de") || message.contains("what is")) {
            log.warn("⚠️ Mensaje Anti-Bot (QUIZ) detectado: {}", message);
            String respuesta = parseAndSolveBotQuiz(message);
            
            if (respuesta != null) {
                log.debug("Lógica Anti-Bot: Respuesta calculada: {}", respuesta);
                Platform.runLater(() -> mainController.appendSystemMessage("🤖 Intentando responder Anti-Bot (QUIZ) con: " + respuesta));
                
                this.sendRawLine(respuesta); 
                
                log.info("✅ Respuesta Anti-Bot enviada: {}", respuesta);
                handledCritically = true;
                return; // Salir si el quiz se resolvió.
            }
        }
        
        // ======================================================================
        // 3. DETECCIÓN DE FINALIZACIÓN (Inicia la sincronización global)
        // ======================================================================
        if (message.contains("VERIFICATION_DONE")) {
            log.info("✅ Verificación Anti-Bot completada. Iniciando sincronización global de canales y usuarios.");
            
            // Inicializar el contador
            this.channelsTestedCount = 0;
            
            // Acciones de sincronización
            this.listChannels(); 
            sendRawLine("WHO *"); 
            
            // Notificación a la UI
            Platform.runLater(() -> {
                mainController.appendSystemMessage("✅ [Sistema] Verificación Anti-Bot exitosa. Solicitando lista global de usuarios y canales...");
            });
            
            handledCritically = true;
        }
        
        // ======================================================================
        // 4. Muestra de NOTICE Genérico (Si no fue un mensaje crítico)
        // ======================================================================
        if (!handledCritically) {
            Platform.runLater(() -> {
                 if (mainController != null) {
                     // Si no fue un mensaje de verificación/quiz, lo mostramos como un NOTICE normal en el Status.
                     mainController.appendSystemMessage(
                         String.format("[NOTICE de %s] %s", source, message)
                     );
                 }
             });
        }
    }

    // ==========================================================
    // EVENTOS DE CANAL Y USUARIO
    // ==========================================================

 

 /**
  * Utiliza el comando WHOIS para verificar el estado de conexión de un usuario.
  * @param nick El nick del usuario a verificar.
  */
 // Dentro de ChatBot.java

    public void checkConnectionStatusViaWhois(String nick) {
        // ⚠️ CRÍTICO: Debe tener una pausa para evitar ser baneado por flood de WHOIS.
        try {
            // Pausa de 100-200 ms entre cada WHOIS.
            Thread.sleep(150); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupción durante la pausa de WHOIS.");
        }
        
        // ⭐ ACCIÓN PRINCIPAL: Enviar el comando IRC al servidor.
        // Asume que 'sendRawLine' es el método de PircBot para enviar comandos crudos.
        sendRawLine("WHOIS " + nick);
    }


    @Override
    protected void onJoin(String channel, String sender, String login, String hostname) {
        super.onJoin(channel, sender, login, hostname);
        
        // Normalizar el nombre del canal para asegurar la consistencia.
        final String lowercaseChannel = channel.toLowerCase();
        log.debug("Event: onJoin. Canal: {}, Usuario: {}", channel, sender);

        // Obtener el envoltorio de la ventana (solo si está abierta)
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(lowercaseChannel);
        
        // ----------------------------------------------------------------------------------
        // ⭐ Lógica de Mantenimiento en Tiempo Real (APLICA SIEMPRE)
        // ----------------------------------------------------------------------------------
        
        // 1. Añadir el nick a la lista global de conectados (a menos que sea el propio bot)
        if (!sender.equalsIgnoreCase(getNick())) {
            
            // Usamos toLowerCase() para la lista global
            if (connectedNicks.add(sender.toLowerCase())) {
                log.debug("JOIN: {} añadido a la lista global. Notificando conexión.", sender);
                
                // 2. Notificar al ChatController para sombreado (si es un usuario conocido)
                Platform.runLater(() -> {
                    mainController.updateConnectionStatus(sender, true);
                });
            }
        }

        // ----------------------------------------------------------------------------------
        // Lógica para cuando TU PROPIO BOT se une al canal (sender == getNick())
        // ----------------------------------------------------------------------------------
        if (sender.equalsIgnoreCase(getNick())) {
            log.debug("Propio JOIN: Registrando unión y solicitando NAMES (sin afectar UI).");
            
            // 1. Registrar el estado de unión
            joinedChannels.add(lowercaseChannel); 
            
            Platform.runLater(() -> {
                // Notificar al usuario principal (solo en la consola de estado)
                mainController.appendSystemMessage("🚪 Has entrado al canal " + channel + ".");
                
                // Forzar la adición del canal a la lista visible de canales disponibles (TableView), si aplica.
                mainController.forzarCanalUnidoEnLista(channel);
            });

            // 2. Solicitar la lista de usuarios. El onUserList() gestionará la respuesta.
            this.sendRawLine("NAMES " + channel); 
            log.debug("Enviado NAMES después del JOIN del propio bot en {}", channel);
        } 
        
        // ----------------------------------------------------------------------------------
        // Lógica para OTROS usuarios que se unen (actualiza lista y contador de ventana)
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
                ventanaWrapper.controller.addUserToList(sender);
                
                log.debug("Usuario {} añadido localmente después de JOIN en {}", sender, channel);
            });
        }
    }

 // Dentro de ChatBot.java

    @Override
    protected void onUserList(String channel, org.jibble.pircbot.User[] users) {
        // Es una buena práctica llamar a super.onUserList, aunque PircBot lo deja vacío.
        super.onUserList(channel, users);
        
        // ======================================================================
        // 1. Lógica de SINCRONIZACIÓN INICIAL (JOIN/PART secuencial)
        //    Propósito: Llenar el Set global connectedNicks (para el sombreado global).
        // ======================================================================
        if (isSyncingChannel) {
            log.debug("SYNC: Recibido onUserList durante sincronización para {}. Total nicks: {}. Recolectando...", channel, users.length);
            
            // Iterar y actualizar la lista global (connectedNicks)
            for (org.jibble.pircbot.User user : users) {
                String nick = user.getNick();
                
                // ⭐ ACCIÓN CRÍTICA: Añadir el nick a la lista global (en minúsculas). ⭐
                if (connectedNicks.add(nick.toLowerCase())) { 
                    log.debug("SYNC: Añadido {} a la lista global desde el canal {}.", nick, channel);
                }
                
                // ⭐ Actualizar el estado de sombreado de usuarios conocidos (si están en la lista global)
                // Se ejecuta en el hilo de JavaFX para manipular la UI.
                Platform.runLater(() -> mainController.updateConnectionStatus(nick, true));
            }
            
            // La finalización de la sincronización se maneja en onEndOfNames (código 366).
            return; 
        }
        
        // ======================================================================
        // 2. Lógica de ACTUALIZACIÓN NORMAL DE CANAL (Fuera de la fase de sync)
        //    Propósito: Actualizar la ventana del CanalController abierto.
        // ======================================================================
        log.debug("Event: onUserList recibido para {}. Total de usuarios: {}", channel, users.length);
        
        // 1. Convertir el array de User a una lista de Strings con el prefijo (@, +, etc.)
        List<String> userNicksWithPrefix = new ArrayList<>();
        for (org.jibble.pircbot.User user : users) {
            // user.getPrefix() devuelve "@", "+" o ""
            // user.getNick() devuelve el nick limpio
            userNicksWithPrefix.add(user.getPrefix() + user.getNick());
        }
        
        // Contamos los usuarios (el contador no incluye el nick del bot si es un LIST/NAMES)
        final int userCount = userNicksWithPrefix.size(); 
        
        // 2. Ejecutar la actualización en el hilo de JavaFX
        Platform.runLater(() -> {
            if (mainController != null) {
                
                // ⭐ A. Actualizar la lista de usuarios en el ListView del canal ⭐
                // Delegamos la lista COMPLETA de nicks (con prefijos) al CanalController.
                mainController.actualizarUsuariosCanal(channel, userNicksWithPrefix);
                
                // ⭐ B. Actualizar el contador de usuarios en el encabezado del canal ⭐
                // Delegamos solo el número total al CanalController.
                mainController.actualizarContadorUsuarios(channel, userCount); 
            }
        });
        
        log.debug("Lista de usuarios y contador ({}) actualizados para {}", userCount, channel);
    }



    @Override
    protected void onPart(String channel, String sender, String login, String hostname) {
        super.onPart(channel, sender, login, hostname);
        
        final String lowercaseChannel = channel.toLowerCase();
        log.debug("Event: onPart. Canal: {}, Usuario: {}", channel, sender);
        
        // ======================================================================
        // 1. Lógica cuando TU PROPIO BOT sale (sender.equalsIgnoreCase(getNick()))
        // ======================================================================
        if (sender.equalsIgnoreCase(getNick())) {
            
            // ⭐ VERIFICACIÓN CRÍTICA PARA LA SINCRONIZACIÓN INICIAL ⭐
            if (isSyncingChannel) {
                log.debug("SYNC: Bot salió de {} con éxito. Moviendo al siguiente canal.", channel);
                
                // 1. Desactivamos el flag de sincronización para evitar llamadas recursivas accidentales
                // y para que el próximo canal pueda empezar limpio.
                // Esto es crucial si 'processNextSyncChannel' establece el flag de nuevo.
                isSyncingChannel = false; 
                
                // ⭐⭐⭐ CORRECCIÓN CRÍTICA: LLAMAR AL MOTOR DEL BUCLE ⭐⭐⭐
                // Asegúrate de que 'processNextSyncChannel()' exista y contenga el chequeo isEmpty().
                processNextSyncChannel(); 
                
                // Ya que estamos en modo SYNC, NO debemos ejecutar la lógica normal de cierre de ventana.
                return; 
            }
            
            // --- Lógica normal de PART del bot (cuando no está sincronizando) ---
            // Se mantiene la lógica original para cuando el usuario manualmente hace /part
            
            log.debug("Propio PART: Cerrando ventana de canal y eliminando de rastreador.");
            
            mainController.removerBotonCanal(channel); 
            // joinedChannels.remove(lowercaseChannel); // (Asegúrate de que esta línea esté comentada o implementada)

            Platform.runLater(() -> {
                mainController.cerrarCanalDesdeVentana(channel); 
                mainController.appendSystemMessage("« Has salido del canal " + channel);
            });
            
        // ======================================================================
        // 2. Lógica para cuando otro usuario sale (PART normal)
        // ======================================================================
        } else {
            // ... (Tu lógica original para usuarios normales que hacen PART) ...
            CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(lowercaseChannel);
            
            if (ventanaWrapper != null) {
                Platform.runLater(() -> {
                    String mensaje = "« " + sender + " ha salido de " + channel;
                    
                    ventanaWrapper.controller.appendSystemMessage(
                        mensaje, 
                        CanalController.MessageType.PART, 
                        sender
                    );
                    
                    ventanaWrapper.controller.removeUserFromList(sender);
                    
                    log.debug("Notificando remoción de usuario {} después de PART en {}", sender, channel);
                });
            }
        }
    }



    @Override
    protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
        super.onQuit(sourceNick, sourceLogin, sourceHostname, reason);
        
        log.debug("Event: onQuit. Usuario: {}, Razón: {}", sourceNick, reason);
        
        // 1. Eliminar el nick de la lista global de conectados.
        // Usamos toLowerCase() para la consistencia en IRC.
        if (connectedNicks.remove(sourceNick.toLowerCase())) {
            log.debug("QUIT: {} eliminado de la lista global. Notificando desconexión.", sourceNick);
            
            // 2. Notificar al controlador para actualizar el estado del UsuarioConocido.
            Platform.runLater(() -> {
                // Este método quitará el sombreado verde si sourceNick es un usuario conocido.
                mainController.updateConnectionStatus(sourceNick, false);
                
                // 3. Mostrar el mensaje de sistema en la ventana principal.
                mainController.appendSystemMessage("« " + sourceNick + " ha abandonado IRC (" + reason + ")");
                
                // ⭐ IMPORTANTE: No necesitamos enviar NAMES. PircBot maneja los eventos PART 
                // en todos los canales donde estaba el usuario y tu lógica en onPart() 
                // debería ser suficiente para limpiar las listas locales del canal.
            });
        } else {
            // El usuario puede haber estado en un canal que el bot no estaba rastreando, 
            // o la lista ya se actualizó. Mostrar solo el mensaje.
            Platform.runLater(() -> {
                mainController.appendSystemMessage("« " + sourceNick + " ha abandonado IRC (" + reason + ")");
            });
        }
    }

 

    @Override
    protected void onNickChange(String oldNick, String login, String hostname, String newNick) {
        super.onNickChange(oldNick, login, hostname, newNick);
        
        log.debug("Event: onNickChange. De {} a {}", oldNick, newNick);
        
        // 1. Actualizar la lista global de nicks
        // Usamos toLowerCase() para la lista global
        if (connectedNicks.remove(oldNick.toLowerCase())) {
            connectedNicks.add(newNick.toLowerCase());
            log.debug("NICK: Lista global actualizada. {} -> {}.", oldNick, newNick);
            
            // 2. Notificar al controlador para que actualice el UsuarioConocido.
            Platform.runLater(() -> {
                // Este método gestiona el cambio de nick en la lista de usuarios conocidos.
                mainController.handleNickChange(oldNick, newNick);
                
                // 3. Mostrar el mensaje de sistema.
                mainController.appendSystemMessage("↔️ " + oldNick + " ahora es conocido como " + newNick);
            });
        } else {
            // El usuario puede no haber sido un usuario conocido, pero mostramos el mensaje.
            Platform.runLater(() -> {
                mainController.appendSystemMessage("↔️ " + oldNick + " ahora es conocido como " + newNick);
            });
        }
        
        // ⭐ ELIMINACIÓN CRÍTICA: Se remueve el bucle for y el sendRawLine("NAMES...")
        // La actualización de las listas de usuarios en las ventanas de canal abiertas
        // es manejada por tu lógica existente en ChatController o por PircBot.
    }

    
    /**
     * Imprime la lista de nicks de todos los usuarios conocidos globalmente.
     * Esto ayuda a verificar si la lista de usuarios se ha poblado correctamente.
     */
    public void printGlobalUserNicks() {
        System.out.println("\n--- INICIO DE LISTA DE NICKNAMES GLOBALES (DEBUG) ---");
        
        // ⭐ USAMOS LA VARIABLE CORRECTA: connectedNicks (Set<String>) ⭐
        // Esta variable ya está declarada como atributo de clase en ChatBot.java
        
        if (connectedNicks != null && !connectedNicks.isEmpty()) {
            int count = 0;
            
            // Iteramos sobre el Set de strings (los nicks)
            for (String nick : connectedNicks) {
                System.out.println("  NICK: " + nick);
                count++;
            }
            
            System.out.println("TOTAL NICKNAMES CONECTADOS EN SET: " + count);
        } else {
            System.out.println("La lista de nicks conectados está vacía o nula. Revise la lógica del case 352 (WHO Reply).");
        }
        
        System.out.println("--- FIN DE LISTA DE NICKNAMES GLOBALES ---\n");
    }
    
 // Dentro de ChatBot.java

    /**
     * Devuelve el conjunto (Set) de todos los nicks conocidos globalmente
     * que están actualmente conectados al servidor.
     * Este método es esencial para que la UI (ChatController) pueda realizar el sombreado.
     */
    public Set<String> getConnectedNicks() {
        // Retornamos el Set que contiene todos los nicks en minúsculas.
        return connectedNicks;
    }
    
 

 // Dentro de ChatBot.java -> finalizeGlobalSync()

    private void finalizeGlobalSync() {
        log.info("🔄 Sincronización global (canales y usuarios) finalizada.");
        isSyncingChannel = false;
        
        final int totalChannels = this.channelsTestedCount;
        final int totalNicks = connectedNicks.size();
        
        // ==========================================================
        // 1. Mostrar las estadísticas en la consola (DEBUG)
        // ==========================================================
        log.info("*** RESUMEN DE SINCRONIZACIÓN GLOBAL ***");
        log.info("Canales procesados (JOIN/PART): {}", totalChannels);
        log.info("Usuarios únicos detectados: {}", totalNicks);
        log.info("**************************************");
        
        // 2. Mostrar la lista de nicks (DEBUG)
        printGlobalUserNicks(); 
        
     // ⭐ LINEA DE DEBUG TEMPORAL ⭐
        System.out.println("DEBUG: El tamaño de connectedNicks es: " + connectedNicks.size());

        // ==========================================================
        // 3. Notificar a la UI (Status Bar)
        // ==========================================================
        Platform.runLater(() -> {
            if (mainController != null) {
                
                // ⭐ ACCIÓN CLAVE: MOSTRAR EL RESUMEN EN EL STATUS BAR ⭐
                String statusMessage = String.format(
                    "✅ Sincronización completa. Canales testeados: %d, Usuarios únicos: %d.", 
                    totalChannels, 
                    totalNicks
                );
                // El método appendSystemMessage() envía el mensaje al área de estado
                mainController.appendSystemMessage(statusMessage);
                
                // Habilita el campo de texto (TextField)
                mainController.syncFinished(); 
            }
        });
    }
    
 

    private void processNextSyncChannel() {
        if (channelsToSync.isEmpty()) {
            finalizeGlobalSync(); 
            return;
        }

        String nextChannel = channelsToSync.poll();
        isSyncingChannel = true; 
        
        // ⭐ NUEVA LÍNEA: Incrementar el contador de canales testeados ⭐
        this.channelsTestedCount++; 
        
        log.info("🔍 Uniéndose temporalmente a {} ({}/{}) para obtener lista de usuarios...", 
                 nextChannel, 
                 this.channelsTestedCount, // Muestra el canal actual
                 (this.channelsTestedCount + channelsToSync.size()) // Muestra el total tentativo
                 ); 
        
        joinChannel(nextChannel); 
    }
    @Override
    protected void onServerResponse(int code, String response) {
        
        // ⭐ NO HAY Platform.runLater ENVOLVIENDO TODO.
        // Esta sección se ejecuta en el hilo de red de PircBot.

        if (mainController == null) return;
        
        // Las variables solo se declararán aquí si son necesarias para lógica no-UI,
        // pero generalmente es más seguro declararlas dentro de los bloques case.
        
        switch (code) {
            
            // ==================================================================
            // 1. Lógica CRÍTICA de SINCRONIZACIÓN (Ejecución Directa en Hilo de Bot)
            // ==================================================================
            
        case 323: // End of LIST (RPL_LISTEND)
            
            // 1. Desactivamos el flag de listado
            isListingChannels = false;
            
            log.info("📊 Fin de la lista de canales. Total de canales a sincronizar (JOIN/PART): {}", channelsToSync.size());
            
            // ⭐ NUEVA LÓGICA CLAVE: INICIAR LA SECUENCIA JOIN/PART ⭐
            
            if (!channelsToSync.isEmpty()) {
                // Si hay canales en la cola (llenos por el 322), iniciamos el proceso de JOINS secuenciales.
                // La finalización de la UI se moverá a finalizeGlobalSync().
                processNextSyncChannel(); 
            } else {
                // Si no se encontró ningún canal para sincronizar (vacío), finalizamos la sincronización global.
                finalizeGlobalSync(); 
            }
            
            break;
            
        // --- Manejo de Errores de JOIN (Necesario para la sincronización) ---
        case 473: // ERR_INVITEONLYCHAN
        case 474: // ERR_BANNEDFROMCHAN
        case 475: // ERR_BADCHANNELKEY
            
            final String[] errorTokens = response.split(" ");
            final String channelFailed = errorTokens.length >= 2 ? errorTokens[1] : "Canal Desconocido";

            // Si el error ocurre durante la fase de JOINS/PARTS secuenciales:
            if (isSyncingChannel) {
                log.warn("⚠️ Saltando canal {} en sincronización debido a error {}: {}", channelFailed, code, response);
                
                // La clave es avanzar al siguiente canal de la cola
                processNextSyncChannel(); 
            }
            break;
                
            

            case 322: // Respuesta LIST (RPL_LIST): Datos de un canal
                
                // Declaración de variables dentro del case
                String[] tokens;
                
                if (isListingChannels) {
                    try {
                        // --- INICIO del PARSEO (pesado) ---
                        String botNick = getNick();
                        int startOfDataIndex = response.indexOf(botNick);
                        
                        if (startOfDataIndex == -1) break; 
                        
                        String dataPart = response.substring(startOfDataIndex + botNick.length()).trim();
                        tokens = dataPart.split(" ", 4); 

                        if (tokens.length < 3) break; 

                        final String channelName = tokens[0]; // <<< Variable 'effectively final' utilizada
                        final int userCount = Integer.parseInt(tokens[1]); 
                        
                        String modos = "";
                        String topic = "";

                        if (tokens.length == 4) {
                            modos = tokens[2];
                            topic = tokens[3].startsWith(":") ? tokens[3].substring(1).trim() : tokens[3];
                        } else if (tokens.length == 3) {
                            topic = tokens[2].startsWith(":") ? tokens[2].substring(1).trim() : tokens[2];
                        }

                        if (!modos.isEmpty() && !modos.startsWith("+")) modos = "+" + modos;
                        
                        // Crear el objeto Canal AQUÍ
                        final Canal canal = new Canal(channelName, userCount, modos, topic); 
                        // --- FIN del PARSEO (pesado) ---

                        // ⭐ SOLO EL PASO FINAL de UI va en Platform.runLater
                        Platform.runLater(() -> {
                            // Usamos la variable 'channelName' (que es final) o 'canal.getChannelName()'
                            // Si la clase Canal tiene el método, usamos el método. Si no, usamos la variable.
                            
                            // Mantenemos la verificación usando la variable que ya creaste.
                            if (currentListReceiver != null && channelName.startsWith("#")) { 
                                currentListReceiver.accept(canal); 
                            }
                        });

                    } catch (Exception e) {
                        log.error("Error al parsear respuesta 322: {}", response, e);
                    }
                }
                break;
                
             

            case 352: // WHO Reply (RPL_WHOREPLY)
                
                // 1. Parseamos la respuesta
                final String[] whoTokens = response.split(" "); 
                
                // Verificamos el formato mínimo
                if (whoTokens.length >= 7) { 
                    
                    // 2. Extraer y hacer finales las partes esenciales
                    final String username = whoTokens[2];         
                    final String hostname = whoTokens[3];         
                    final String server = whoTokens[4];         
                    final String nickName = whoTokens[5]; // ⭐ NICKNAME ⭐      
                    final String flags = whoTokens[6];         

                    int realNameStartIndex = response.indexOf(":");
                    final String rawRealName = (realNameStartIndex != -1) ? 
                        response.substring(realNameStartIndex + 1).trim() : "";
                    
                    final String realName = rawRealName.startsWith("0 ") ? 
                        rawRealName.substring(2).trim() : rawRealName; 

                    // 3. Crear el objeto IRCUser fuera del hilo de UI
                    final IRCUser user = new IRCUser(nickName, username + "@" + hostname, flags, server, realName);

                    // ⭐ 4. ALMACENAMIENTO DE NICKNAME EN LA LISTA GLOBAL ⭐
                    // Esto asegura que el nick esté en tu lista maestra para el resaltado en verde.
                    // Usamos toLowerCase() para la comparación insensible a mayúsculas/minúsculas.
                    connectedNicks.add(nickName.toLowerCase()); 
                    
                    // 🚨 Si también usas un Map<String, IRCUser> para datos completos (e.g., knownUsers), agrégalo aquí:
                    // knownUsers.put(nickName.toLowerCase(), user);


                    // --- LÓGICA DE NOTIFICACIÓN A LA UI (Platform.runLater) ---
                    Platform.runLater(() -> {
                        // 5. Notificar al controlador que está ejecutando el WHO.
                        if (activeLWhoController != null) {
                            try {
                                activeLWhoController.receiveUser(user);
                            } catch (Exception e) {
                                log.error("Error al procesar respuesta 352 en UI: {}", response, e);
                            }
                        }
                    });
                } else {
                    log.warn("Respuesta 352 incompleta: {}", response);
                }
                
                break;

            	case 315: // End of WHO List (RPL_ENDOFWHO)
                
                // ... (tu lógica de parseo) ...

                // ⭐⭐ LÍNEA DE DEBUG DEL TAMAÑO AQUÍ (DONDE SE CONFIRMA EL LLENADO) ⭐⭐
                log.info("Tamaño de connectedNicks después de WHO global: {}", connectedNicks.size());
                
                // 1. ANULAR EL BUCLE LENTO DE JOIN/PART
                // Si el WHO global ya llenó la lista, la sincronización por canal ya no es necesaria.
                channelsToSync.clear(); 
                isSyncingChannel = false; 
                
                // --- LÓGICA DE UI EN PLATFORM.runLater ---
                Platform.runLater(() -> {
                    
                    // 2. Notificar al controlador de la UI que la sincronización ha terminado
                    if (mainController != null) {
                        mainController.syncFinished(); // Habilita el TextField y finaliza el estado de carga
                    }
                    
                    // ... (otras finalizaciones) ...
                });
                break;

             // ... otros casos ...
            case 353: // NAMES Reply (Contiene la lista de usuarios del canal)
            case 366: // End of NAMES (Fin de la lista de usuarios del canal)
                
                // 1. Parseamos los tokens en el hilo de background.
                final String[] nameTokens = response.split(" ");
                final int finalCode = code; 
                final String finalResponse = response; 
                
                // 2. Determinamos el canal de manera final, dependiendo del código de respuesta.
                String tempNamesChannel = null;

                if (code == 353 && nameTokens.length >= 5) {
                    // Formato 353: :server 353 nick = #channel :@user +user user
                    tempNamesChannel = nameTokens[4];
                } else if (code == 366 && nameTokens.length >= 4) {
                     // Formato 366: :server 366 nick #channel :End of /NAMES list.
                     tempNamesChannel = nameTokens[3];
                }

                final String namesChannel = tempNamesChannel; 

                // ======================================================================
                // ⭐ LÓGICA DE CONTROL DE SINCRONIZACIÓN GLOBAL (AQUÍ USAMOS EL 366) ⭐
                // ======================================================================
                if (finalCode == 366 && isSyncingChannel && namesChannel != null) {
                    log.info("✅ 366 (End of NAMES) recibido para {}. Es la señal para salir.", namesChannel);
                    
                    // 3. ENVIAR EL PART: Esto es necesario para avanzar al siguiente canal.
                    // La llamada a processNextSyncChannel() se hará en el onPart() subsiguiente.
                    partChannel(namesChannel);
                    
                    // Devolvemos el control para que el código de delegación del CanalController no se ejecute, 
                    // ya que estamos en una fase de sincronización especial.
                    // Si el CanalController necesita saber que se terminó el 366, podríamos mantener la llamada delegada, 
                    // pero por seguridad durante la sincronización temporal, lo ignoramos.
                    return; 
                }
                
                // ======================================================================
                // LÓGICA DE DELEGACIÓN ESTÁNDAR (Actualización de la ventana del canal)
                // Se ejecuta solo si NO estamos sincronizando O si el código no es 366 
                // y estamos esperando el NAMES (353) para una ventana abierta.
                // ======================================================================
                if (namesChannel != null && namesDelegates.containsKey(namesChannel)) {
                    
                    // --- LÓGICA DE UI EN PLATFORM.runLater ---
                    Platform.runLater(() -> {
                        
                        CanalController delegate = namesDelegates.get(namesChannel);
                        if (delegate != null) {
                            // Pasamos los valores finales ya calculados.
                            delegate.handleNamesResponse(finalCode, finalResponse);
                        }
                    });
                }
                break;
                
            default:
                // No hace nada por defecto
                break;
        }
    }
    
    /**
     * ➡️ Procesa el siguiente canal en la cola de sincronización. 
     * Esta es la lógica recursiva que mantiene el proceso asíncrono.
     */
    private void syncNextChannel() {
        String nextChannel = channelsToSync.poll();
        
        if (nextChannel != null) {
            isSyncingChannel = true;
            // ⭐ 1. Unirse al canal temporalmente! El onUserList() capturará los nicks.
            joinChannel(nextChannel); 
        } else {
            // ⭐ 2. ¡La cola está vacía! La sincronización inicial ha terminado.
            isSyncingChannel = false;
            // Notificar al controlador para que muestre la UI.
            mainController.syncFinished();
            log.debug("✅ Sincronización inicial de usuarios completada. UI desbloqueada.");
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