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
import java.io.IOException;



/**
 * ChatBot extiende PircBot 1.5.0 y maneja la conexión, eventos IRC y DCC,
 * fusionando toda la lógica de la antigua IrcEventListener.
 */
public class ChatBot extends PircBot {
    private static final Logger log = LoggerFactory.getLogger(ChatBot.class);
    private final ChatController mainController;
    
    // --- CAMPOS PARA LA LISTA DE CANALES ---
 
    private final List<Canal> canalesDisponibles = new ArrayList<>(); 
    // Referencia al Consumer de la UI (para sincronización final, opcional)
    private Consumer<List<Canal>> listFinalReceiver = null;
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
    private String whoQueryChannel;
 
    private ChatController chatController;

 // Nuevo método para lanzar la lista y configurar el receptor final
    public void getCanales(Consumer<List<Canal>> finalReceiver) {
        if (isListingChannels) {
            // Ya está en curso, evita lanzar múltiples veces
            return;
        }
        
        // 1. Limpiar la lista anterior y configurar el receptor final
        this.canalesDisponibles.clear();
        this.listFinalReceiver = finalReceiver;
        this.isListingChannels = true;
        
        // 2. Enviar el comando al servidor
        this.listChannels(); // Este es el método de PircBot que envía "LIST"
    }

 // ⭐ 1. Lista global de todos los nicks conectados al servidor. 
 // Mantenida y actualizada por los eventos JOIN/QUIT.
 private final Set<String> connectedNicks = new HashSet<>();

 // ⭐ 2. Cola para la sincronización inicial: canales a unirse temporalmente.
 private final Queue<String> channelsToSync = new LinkedList<>();

 // ⭐ 3. Bandera para controlar la fase de sincronización inicial (JOIN/PART secuencial).
 private boolean isSyncingChannel = false;
 

private boolean ircLoginCompleted = false; // Nueva bandera de estado


//Método que debe reemplazar a bot.isConnected() para la UI
public boolean isIrcLoginCompleted() {
  return ircLoginCompleted;
}
 
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

//Dentro de ChatBot.java

/**
* Inicia la consulta WHO al servidor para el canal dado y establece el delegado receptor.
* (Tu LWhoController llama a este método).
*/
public void requestWhoList(String channel, LWhoController controller) {
  if (!isConnected()) return;
  
  // ⭐ Almacenar el delegado y el canal de la consulta ⭐
  this.activeLWhoController = controller; 
  this.whoQueryChannel = channel;
  
  sendRawLine("WHO " + channel);
  log.debug("Enviado comando WHO para: {}", channel);
}

/**
* Limpia el estado de la consulta WHO después de recibir 315.
*/
private void clearWhoDelegate() {
 this.activeLWhoController = null;
 this.whoQueryChannel = null;
 log.debug("Delegado WHO limpiado.");
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

 // Dentro de ChatBot.java

    @Override
    protected void onConnect() {
        final ChatController uiController = this.mainController; 
        final String serverUrl = getServer();

        // ⭐ 1. VERIFICACIÓN CRÍTICA: Impedir el NullPointerException
        if (uiController == null) {
            log.error("❌ FATAL: mainController (ChatController) no fue inicializado en el constructor.");
            return; 
        }
        
        // 2. Bloquear la UI y notificar (Debe ir en el hilo de JavaFX)
        Platform.runLater(() -> {
            uiController.getInputField().setDisable(true); 
            uiController.appendSystemMessage("✅ Conectado al servidor: " + serverUrl);
            uiController.appendSystemMessage("⚠️ Esperando mensaje de verificación Anti-Bot...");
            
            // [Añadir código para mostrar el indicador de progreso o pantalla de carga]
        });

        // ⭐ 3. DETECCIÓN DE SERVIDOR LOCAL Y ARRANQUE INMEDIATO ⭐
        if (serverUrl != null && serverUrl.equalsIgnoreCase("irc.example.org")) {
            log.warn("Servidor local detectado. Iniciando sincronización en onConnect.");
            // Ejecutamos la sincronización de inmediato
            iniciarSincronizacionGlobal(); 
            // No necesitamos la rutina del Thread, ya que los JOINs fallarán de todos modos
            // sin un delay que imite la verificación, pero usaremos el Thread para IDENTIFY.
        }

        // 4. Rutina de comandos, JOINs y Sincronización en un hilo separado
        new Thread(() -> {
            try {
                Thread.sleep(1000); 

                // 4a. Identificación con NickServ
                String password = uiController.getPassword();
                if (password != null && !password.isEmpty()) {
                    this.sendMessage("NickServ", "IDENTIFY " + password);
                    log.debug("🔐 Enviando comando IDENTIFY a NickServ.");
                    Thread.sleep(1500); 
                }

                // 4b. Ejecutar la Secuencia de Inicio (Comandos raw personalizados)
                if (uiController.isSecuenciaInicioActivada()) {
                    uiController.ejecutarSecuenciaInicio(true);
                    Thread.sleep(1000);
                }

                // 4c. Unión a canales automáticos
                // Opcional: Solo unirse aquí si NO es el servidor local,
                // o si el servidor local necesita un delay POST-IDENTIFY.
                if (serverUrl == null || !serverUrl.equalsIgnoreCase("irc.example.org")) {
                     String[] canales = {"#tester", "#chat"}; // O la lista dinámica que uses
                     log.debug("Iniciando rutina de auto-join a canales predeterminados (puede fallar sin verificación).");
                     for (String canal : canales) {
                         this.joinChannel(canal);
                         log.debug("🔹 Enviado JOIN para canal: {}", canal);
                         Thread.sleep(500); 
                     }
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

 

 // Dentro de ChatBot.java

    @Override
    protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String message) {
        
        log.debug("Event: onNotice. Fuente: {}, Objetivo: {}", sourceNick, target);
        String source = sourceNick != null ? sourceNick : getServer();
        final String serverUrl = getServer();

        // Siempre mostrar el mensaje en el log del sistema
        log.info("[NOTICE de {}] {}", source, message);
        
        // Bandera para saber si el NOTICE se manejó críticamente 
        boolean handledCritically = false;

        // ======================================================================
        // 1. DETECCIÓN DEL AVISO DE VERIFICACIÓN (Mensajes de URL/Código)
        // ======================================================================
        if (message.contains("Necesitas verificar que no eres un bot") || message.contains("valida tu conexión") || message.contains("URL") || message.contains("http")) { 
            
            // ... (Tu lógica de aviso de verificación se mantiene intacta) ...
            log.warn("⚠️ Aviso de verificación Anti-Bot detectado. Mostrando URL/Código.");
            
            Platform.runLater(() -> {
                if (mainController != null) {
                    
                    mainController.appendSystemMessage("--- ⚠️ MENSAJE DE VALIDACIÓN CRÍTICO ⚠️ ---");
                    mainController.appendSystemMessage("➡️ SERVER: " + message); 
                    mainController.appendSystemMessage("--- --------------------------------- ---");
                    
                    mainController.syncFinished(); // Habilita el input
                    
                    mainController.appendSystemMessage("💬 Por favor, ¡COPIA y PEGA el código de validación AQUÍ o RESUELVE el QUIZ!.");
                }
            });
            handledCritically = true;
        }

        // ======================================================================
        // 2. Lógica de Respuesta a Quizzes Anti-Bot (Cálculos)
        // ======================================================================
        if (message.contains("sum") || message.contains("calcula") || message.contains("resultado de") || message.contains("what is")) {
            // ... (Tu lógica de respuesta a QUIZ se mantiene intacta) ...
            log.warn("⚠️ Mensaje Anti-Bot (QUIZ) detectado: {}", message);
            String respuesta = parseAndSolveBotQuiz(message);
            
            if (respuesta != null) {
                log.debug("Lógica Anti-Bot: Respuesta calculada: {}", respuesta);
                Platform.runLater(() -> mainController.appendSystemMessage("🤖 Intentando responder Anti-Bot (QUIZ) con: " + respuesta));
                
                this.sendRawLine(respuesta); 
                
                log.info("✅ Respuesta Anti-Bot enviada: {}", respuesta);
                handledCritically = true;
                return; 
            }
        }
        
        // ======================================================================
        // 3. DETECCIÓN DE FINALIZACIÓN (Inicia la sincronización global)
        // ======================================================================
        if (message.contains("VERIFICATION_DONE")) {
            
            // ⭐ VERIFICACIÓN CRÍTICA: EVITAR DOBLE INICIALIZACIÓN EN SERVIDOR LOCAL ⭐
            if (serverUrl != null && serverUrl.equalsIgnoreCase("irc.example.org")) {
                log.warn("VERIFICATION_DONE ignorado en {}. La sincronización ya fue forzada en onConnect.", serverUrl);
                handledCritically = true;
                return; 
            }
            
            // --- Lógica normal para otros servidores ---
            log.info("✅ Verificación Anti-Bot completada. Iniciando sincronización global de canales y usuarios.");
            
            // Llama al método refactorizado.
            iniciarSincronizacionGlobal(); 
            
            handledCritically = true;
        }
        
        // ======================================================================
        // 4. Muestra de NOTICE Genérico
        // ======================================================================
        if (!handledCritically) {
            Platform.runLater(() -> {
                 if (mainController != null) {
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
        String userHostIdentity = sender + "@" + login + "." + hostname; 
        
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
                
                // La variable 'userHostIdentity' ya ha sido declarada y construida arriba.
                
                // 1. Notificar la unión en la ventana de chat
                ventanaWrapper.controller.appendSystemMessage(
                    "» " + sender + " se ha unido a " + channel, 
                    CanalController.MessageType.JOIN, 
                    sender,
                    userHostIdentity // ⬅️ Variable accesible y usada aquí
                );
                
                // 2. Agregar el nuevo usuario a la lista local y actualizar el contador.
                ventanaWrapper.controller.addUserToList(sender);
                
                log.debug("Usuario {} añadido localmente después de JOIN en {}", sender, channel);
            });
        }
    }

 

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
        
        // --- CONSTRUCCIÓN DE LA MÁSCARA DEL USUARIO QUE SALE ---
        // Esta identidad es crucial para la nueva funcionalidad del CanalController.
        // Se declara aquí para ser accesible en los bloques Platform.runLater().
        final String userHostIdentity = sender + "@" + login + "." + hostname;
        
        // ======================================================================
        // 1. Lógica cuando TU PROPIO BOT sale (sender.equalsIgnoreCase(getNick()))
        // ======================================================================
        if (sender.equalsIgnoreCase(getNick())) {
            
            // ⭐ Lógica de Sincronización (Se mantiene intacta) ⭐
            if (isSyncingChannel) {
                log.debug("SYNC: Bot salió de {} con éxito. Moviendo al siguiente canal.", channel);
                isSyncingChannel = false; 
                processNextSyncChannel(); 
                return; 
            }
            
            // --- Lógica normal de PART del bot (cuando no está sincronizando) ---
            log.debug("Propio PART: Cerrando ventana de canal y eliminando de rastreador.");
            
            mainController.removerBotonCanal(channel); 

            Platform.runLater(() -> {
                mainController.cerrarCanalDesdeVentana(channel); 
                
                // 💡 NOTA: Aquí se llama a appendSystemMessage del mainController (Status Box), 
                // que probablemente solo espera un argumento (String mensaje).
                // Si el mainController ha sido modificado para requerir 4 args, también debe corregirse.
                // Asumo que esta línea está bien:
                mainController.appendSystemMessage("« Has salido del canal " + channel); 
            });
            
        // ======================================================================
        // 2. Lógica para cuando otro usuario sale (PART normal)
        // ======================================================================
        } else {
            CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(lowercaseChannel);
            
            if (ventanaWrapper != null) {
                Platform.runLater(() -> {
                    String mensaje = "« " + sender + " ha salido de " + channel;
                    
                    // ⭐ LLAMADA CORREGIDA: Incluye el cuarto argumento (userHostIdentity) ⭐
                    ventanaWrapper.controller.appendSystemMessage(
                        mensaje, 
                        CanalController.MessageType.PART, 
                        sender,
                        userHostIdentity // ⬅️ CUARTO ARGUMENTO AÑADIDO
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
        
        // ⭐ PASO CLAVE: CONSTRUIR LA IDENTIDAD COMPLETA
        final String userHostIdentity = sourceNick + "@" + sourceLogin + "." + sourceHostname;
        
        // 1. Intentar eliminar el nick de la lista global de conectados.
        if (connectedNicks.remove(sourceNick.toLowerCase())) {
            log.debug("QUIT: {} eliminado de la lista global. Notificando desconexión.", sourceNick);
            
            // 2. Notificar al controlador principal.
            Platform.runLater(() -> {
                mainController.updateConnectionStatus(sourceNick, false);
                
                // ⭐ LLAMADA CORREGIDA: Incluye la máscara de host como tercer argumento
                // Pasamos 'null' para el canal para indicar que es un QUIT global.
                mainController.notificarSalidaUsuario(sourceNick, null, userHostIdentity); 
            });
            
        } else {
            Platform.runLater(() -> {
                // ⭐ LLAMADA CORREGIDA: Incluye la máscara de host como tercer argumento
                // Se notifica la salida para que se muestre el mensaje.
                mainController.notificarSalidaUsuario(sourceNick, null, userHostIdentity); 
            });
        }
    }

 // En ChatBot.java

    @Override
    protected void onNickChange(String oldNick, String login, String hostname, String newNick) {
        super.onNickChange(oldNick, login, hostname, newNick);
        
        log.debug("Event: onNickChange. De {} a {}", oldNick, newNick);
        
        // ⭐ PASO CLAVE: CONSTRUIR LA IDENTIDAD COMPLETA
        // Usamos el newNick en la máscara, ya que es la identidad actual.
        final String userHostIdentity = newNick + "@" + login + "." + hostname;
        
        // 1. Actualizar la lista global de nicks
        boolean wasConnected = connectedNicks.remove(oldNick.toLowerCase());
        if (wasConnected) {
            connectedNicks.add(newNick.toLowerCase());
            log.debug("NICK: Lista global actualizada. {} -> {}.", oldNick, newNick);
        }
        
        // 2. Notificar a la UI
        Platform.runLater(() -> {
            // Actualizar el estado global de nicks conocidos y el List View principal (si aplica)
            mainController.handleNickChange(oldNick, newNick);
            
            // Mostrar el mensaje de sistema en la ventana principal (Status Box).
            mainController.appendSystemMessage("↔️ " + oldNick + " ahora es conocido como " + newNick);
            
            // 3. ⭐ LÓGICA NUEVA: ITERAR y actualizar ListViews de CANALES ABIERTOS
            // Asumiendo que mainController.getCanalesAbiertos() devuelve Map<String, CanalVentana>
            for (Map.Entry<String, CanalVentana> entry : mainController.getCanalesAbiertos().entrySet()) {
                CanalController controller = entry.getValue().controller;
                
                if (controller != null) {
                    // Llamamos al nuevo método para que el CanalController se encargue de su lista local.
                    controller.handleNickChange(oldNick, newNick, userHostIdentity);
                }
            }
        });
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
 
 // Dentro de ChatBot.java

    @Override
    protected void onServerResponse(int code, String response) {
        super.onServerResponse(code, response); 

        // ======================================================================
        // ⭐ 1. LÓGICA DE SINCRONIZACIÓN DE CANALES (LIST - 322/323)
        // ======================================================================
        if (isListingChannels) {
            switch (code) {
         

            case 322: // RPL_LIST: Respuesta de un canal
                
                // Si la bandera de listado no está activa, salimos inmediatamente.
                if (!isListingChannels) return; 

                // Usaremos el 'response' sin el prefijo 322 que PircBot ya eliminó.
                // Formato esperado: <nickname> <#canal> <usuarios> :[modos...] <topic>

                try {
                    // Usamos el split en la respuesta original (sin códigos de color) para obtener los tokens fijos.
                    String[] fullParts = response.split(" ");
                    
                    if (fullParts.length < 4) { 
                        log.warn("Formato 322 incompleto (menos de 4 tokens). Línea: {}", response);
                        return;
                    }

                    // 1. Extracción de Nombre de Canal (Índice 1)
                    String canalNombre = fullParts[1];
                    
                    if (!canalNombre.startsWith("#")) {
                        // Fallback si el índice 1 no es el canal.
                        canalNombre = ""; 
                        for (int i = 1; i < fullParts.length; i++) {
                            if (fullParts[i].startsWith("#")) {
                                canalNombre = fullParts[i];
                                break;
                            }
                        }
                        if (canalNombre.isEmpty()) {
                            log.warn("No se encontró el nombre del canal (#) en la línea: {}", response);
                            return;
                        }
                    }
                    
                    // 2. Extracción de Número de Usuarios (Índice 2)
                    String usersRaw = (fullParts.length > 2) ? fullParts[2] : ""; 
                    int numUsuarios = 0;
                    
                    try {
                        numUsuarios = Integer.parseInt(usersRaw);
                    } catch (NumberFormatException e) {
                        log.warn("Formato numUsuarios desconocido en 322: '{}' para canal {}", usersRaw, canalNombre);
                        return; 
                    }

                    // **A partir de aquí, usamos la línea LIMPIA para Modos y Descripción.**
                    String cleanedResponse = stripIrcFormatting(response);

                    // 3. Extracción de Modos o Permisos (Basado en ':[...]' )
                    String modos = "[Sin modos]";
                    
                    int modeStart = cleanedResponse.indexOf(":["); 
                    
                    if (modeStart != -1) {
                        int modeEnd = cleanedResponse.indexOf("]", modeStart);
                        
                        if (modeEnd != -1) {
                            // Extraemos la cadena completa (ej. ":[+Cnrt]").
                            String modosFull = cleanedResponse.substring(modeStart, modeEnd + 1).trim();
                            
                            // Eliminamos el prefijo si existe. Ej. "[+Cnrt]"
                            if (modosFull.startsWith(":")) {
                                modos = modosFull.substring(1); 
                            } else {
                                modos = modosFull;
                            }
                        }
                    }

                 
                 

                         // 4. Extracción de Descripción (Topic) con tu regla específica:
                         String descripcionBruta;
                         String descripcionFinal = "[Sin tema]";
                         
                         final int MAX_LENGTH = 140;
                         final String SUFFIX = ".....";

                         // Paso 4a: Encontrar el punto de inicio de la descripción según tu regla.
                         // Regla: "todo lo que haya desde el final de la cadena hasta encontrarse con la primer carácter ']'"
                         
                         int closingBracketIndex = cleanedResponse.lastIndexOf(']');
                         
                         if (closingBracketIndex != -1 && closingBracketIndex < cleanedResponse.length() - 1) {
                             // La descripción comienza *después* del ']'
                             descripcionBruta = cleanedResponse.substring(closingBracketIndex + 1).trim();
                             
                             // Paso 4b: Aplicar el Recorte de 40 caracteres a la descripciónBruta
                             if (descripcionBruta.length() > MAX_LENGTH) {
                                 // Si es más larga que 40, recortamos y añadimos el sufijo.
                                 descripcionFinal = descripcionBruta.substring(0, MAX_LENGTH) + SUFFIX;
                             } else if (!descripcionBruta.isEmpty()) {
                                 // Si es 40 o menos, usamos la descripción completa.
                                 descripcionFinal = descripcionBruta;
                             }
                         }
                         
                         // Fallback: Si no se encuentra ']', o está al final, volvemos a la lógica estándar
                         // de buscar el último ':' después del número de usuarios.
                         if (descripcionFinal.equals("[Sin tema]")) {
                             
                              // Buscamos el ':' que sigue al número de usuarios (fullParts[2])
                              int usersEndIndex = cleanedResponse.indexOf(fullParts[2]);
                              
                              if (usersEndIndex != -1) {
                                  int colonTopicStart = cleanedResponse.indexOf(":", usersEndIndex);
                                  
                                  if (colonTopicStart != -1 && colonTopicStart + 1 < cleanedResponse.length()) {
                                      descripcionBruta = cleanedResponse.substring(colonTopicStart + 1).trim();
                                      
                                      // Aplicamos el recorte al Fallback
                                      if (descripcionBruta.length() > MAX_LENGTH) {
                                          descripcionFinal = descripcionBruta.substring(0, MAX_LENGTH) + SUFFIX;
                                      } else if (!descripcionBruta.isEmpty()) {
                                          descripcionFinal = descripcionBruta;
                                      }
                                  }
                              }
                         }

                         // 5. Crear objeto y almacenar en la lista global
                         final Canal canal = new Canal(canalNombre, numUsuarios, modos, descripcionFinal); 
                         
                         log.info("📊 PARSEADO OK: Canal={}, Usuarios={}, Modos={}, Desc={}", canalNombre, numUsuarios, modos, descripcionFinal);

                         // Envío seguro al hilo de JavaFX
                         Platform.runLater(() -> {
                             canalesDisponibles.add(canal); 
                         });

                     

                } catch (Exception e) {
                    log.error("Error FATAL al parsear 322: {}", response, e);
                }
                return;

                    
             // Dentro de ChatBot.java -> onServerResponse(int code, String response)

            case 323: // RPL_LISTEND: Fin de la lista de canales
                log.info("✅ Recibido 323: Fin de la lista de canales. Total: {} canales.", canalesDisponibles.size());
                
                isListingChannels = false;
                
                // Notificamos a la UI con la lista COMPLETA de una sola vez
                final Consumer<List<Canal>> finalReceiver = this.listFinalReceiver;
                final List<Canal> listaFinal = new ArrayList<>(canalesDisponibles); // Copia segura
                
                Platform.runLater(() -> {
                    if (finalReceiver != null) { 
                        finalReceiver.accept(listaFinal); // Envía toda la lista
                    }
                    this.listFinalReceiver = null; // Anulamos el receptor
                });
                
                return;
            }
            // Si estábamos en isListingChannels pero el código no era 322/323, continúa abajo.
        }
        
     // ⭐ 2a. Lógica de Delegado LWho (352 y 315 para ventana) ⭐
        if (activeLWhoController != null && whoQueryChannel != null) {
            
            if (code == 352) { // RPL_WHOREPLY - Usuario individual para la ventana LWho
                // Verificamos que sea la respuesta para el canal que consultamos.
                if (response.contains(whoQueryChannel)) {
                    
                    IRCUser user = parseWhoResponse(response);
                    
                    if (user != null) {
                        Platform.runLater(() -> {
                            // Enviamos el usuario al delegado para que lo añada a su TableView
                            // ¡Aquí activeLWhoController aún DEBE SER válido!
                            activeLWhoController.receiveUser(user); 
                        });
                    }
                }
            } 
            
            else if (code == 315) { // RPL_ENDOFWHO - Fin de la consulta para la ventana LWho
                if (response.contains(whoQueryChannel)) {
                    
                    Platform.runLater(() -> {
                        // 1. Notificamos al delegado que la consulta ha finalizado.
                        activeLWhoController.finishQuery(whoQueryChannel); 
                        
                        // 2. ⭐⭐ MOVIMIENTO CRÍTICO: Limpiamos el delegado DENTRO del Platform.runLater(). ⭐⭐
                        // Esto asegura que la limpieza es la última acción ejecutada en la cola FX,
                        // después de que todos los 352 (receiveUser) ya se hayan procesado.
                        clearWhoDelegate(); 
                    });
                }
            }
        }
        
        // ⭐ 2b. Lógica General (Después de la ventana LWho) ⭐
        switch (code) {
            
            case 001: // RPL_WELCOME
            case 376: // RPL_ENDOFMOTD
                // ... (Tu lógica existente para activar la bandera ircLoginCompleted) ...
                if (!ircLoginCompleted) {
                    log.info("✅ IRC Login confirmado con código {}. Habilitando isIrcLoginCompleted.", code);
                    this.ircLoginCompleted = true; 
                }
                break;

            case 352: { // WHO Reply (RPL_WHOREPLY) - SINCRONIZACIÓN GLOBAL
                // Aquí MANTENEMOS la lógica para añadir nicks a la lista global conectada (connectedNicks), 
                // ya que esta consulta se usa para el inicio del Bot (WHO *)
                
                final String[] whoTokens = response.split(" "); 
                if (whoTokens.length >= 7) { 
                    final String nickName = whoTokens[5];
                    connectedNicks.add(nickName.toLowerCase()); 
                    // Nota: Si el Bot hace WHO *, esta lógica se dispara para el LISTADO GLOBAL.
                }
                break; 
            }
                 
            case 315: { // End of WHO List (RPL_ENDOFWHO) - SINCRONIZACIÓN GLOBAL
                // Este es el fin de la consulta WHO * (sincronización global)
                
                log.info("✅ Recibido 315: Sincronización global de nicks completada. Total: {}", connectedNicks.size());
                
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.syncFinished(); // Refresca la lista global de conocidos.
                    }
                });
                break;
            }

            // ======================================================================
            // ⭐ 3. LÓGICA DE USUARIOS EN CANAL (NAMES - 353/366)
            // ======================================================================
            case 353: // NAMES Reply (RPL_NAMREPLY)
            case 366: { // End of NAMES (RPL_ENDOFNAMES)
                final String namesChannel = (code == 353 && response.split(" ").length >= 5) ? 
                                             response.split(" ")[4] : 
                                           (code == 366 && response.split(" ").length >= 4) ? 
                                             response.split(" ")[3] : null;

                if (namesChannel != null && namesDelegates.containsKey(namesChannel)) {
                    final CanalController delegate = namesDelegates.get(namesChannel);
                    final int finalCode = code;
                    
                    Platform.runLater(() -> {
                        delegate.handleNamesResponse(finalCode, response);
                    });

                    if (code == 366) {
                        log.info("Canal {} sincronizado. Nicks enviados al controlador.", namesChannel);
                    }
                }
                break;
            } 

            default:
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
    
 // Añade esta función a tu clase ChatBot
    private String stripIrcFormatting(String text) {
        if (text == null) {
            return "";
        }
        // 1. Eliminar códigos de color ([0-9]{0,2}(,[0-9]{0,2})?)
        text = text.replaceAll("\\u0003\\d{0,2}(,\\d{0,2})?", "");
        // 2. Eliminar otros códigos de formato (negrita, subrayado, inversa, restablecer)
        text = text.replaceAll("[\\u0002\\u001f\\u0016\\u000f]", "");
        return text;
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
    
 // Dentro de ChatBot.java

    /**
     * Elimina el canal del Set interno que rastrea los canales unidos.
     * Esto es crucial para que isJoined() devuelva false después de un /part manual.
     */
    public void removeChannelFromJoinedState(String channelName) {
        // Asegúrate de que 'joinedChannels' es el Set que usas para rastrear el estado
        // y de que manejas las mayúsculas/minúsculas de forma consistente (aquí, en minúsculas).
        joinedChannels.remove(channelName.toLowerCase());
    }
    
 // Dentro de ChatBot.java

    /**
     * Centraliza la lógica para iniciar la sincronización global de canales y usuarios,
     * y habilita la interfaz de usuario.
     * Se llama desde onConnect (para servidores locales) o desde onNotice (para VERIFICATION_DONE).
     */
    private void iniciarSincronizacionGlobal() {
        final ChatController uiController = this.mainController;
        
        // 1. Inicializar el contador para listChannels (si aplica)
        this.channelsTestedCount = 0; 
        
        // 2. Acciones de sincronización
        this.listChannels(); // Pedir lista de canales
        this.sendRawLine("WHO *"); // Pedir lista de usuarios globales
        
        // 3. Notificación a la UI
        Platform.runLater(() -> {
            if (uiController != null) {
                uiController.appendSystemMessage("✅ [Sistema] Inicialización de sincronización global...");
                
                // Habilita el campo de comandos y otros elementos de la UI (Input Field)
                uiController.syncFinished(); 
            }
        });
    }
    

 

 // Dentro de ChatBot.java (Método Auxiliar)

    /**
     * Parsea la línea de respuesta 352 del servidor, ajustándose al formato parcial 
     * que PircBot parece estar entregando (omitiendo el prefijo :servidor 352).
     * Formato recibido: nick_bot #canal user host servidor nick flags hops :realName
     *
     * @param response La línea que comienza después de ":servidor 352 ".
     * @return Un objeto IRCUser con los campos rellenados.
     */
    private IRCUser parseWhoResponse(String response) {
        try {
            // La línea comienza con El_ArWeN #canal...
            // Usamos una división que NO limite el número de partes.
            String[] parts = response.split(" "); 
            
            // Necesitamos al menos 7 partes: nick_bot, #canal, user, host, server, nick, flags/resto.
            if (parts.length < 7) {
                log.warn("Formato 352 inesperado (menos de 7 tokens). Línea: {}", response);
                return null;
            }

            // ⭐⭐ ASIGNACIÓN DE ÍNDICES CORREGIDA ⭐⭐
            // El nick del bot (parts[0]) lo descartamos, ya que no es el nick del usuario en el canal.
            String channel = parts[1]; // #el_jardin_musical
            String user = parts[2];    // sgomx o radio o eljardinm
            String host = parts[3];    // i-n44.bsi.qrubuv.IP o la.musica...
            String server = parts[4];  // lima.chatzona.org  (4ª Columna)
            String nick = parts[5];    // SeRgi0 o ElJardinMusical o MaravillaDj (1ª Columna)
            
            // El segmento de Flags (Ej: G+r :0 I3wjfCN8L...) comienza en parts[6]
            String flagsRaw = parts[6]; 
            String flags = flagsRaw.replaceFirst("[HG]", ""); // Quitamos G/H -> (+r) o (@r) (3ª Columna)

            // --- 2. Extracción del Nombre Real (5ª Columna) ---
            // El Nombre Real es todo lo que está después del primer ':' en este formato truncado.
            
            String realName = "N/A - Parsing Error";
            
            // Buscamos el índice del primer ':' después de parts[6]
            // Concatenamos las partes restantes para buscar el ":"
            StringBuilder remaining = new StringBuilder();
            for (int i = 6; i < parts.length; i++) {
                remaining.append(parts[i]).append(" ");
            }
            String remainingString = remaining.toString().trim();

            // El Nombre Real comienza después del primer ':' en esta subcadena
            int firstColonIndex = remainingString.indexOf(":"); 

            if (firstColonIndex != -1 && firstColonIndex + 1 < remainingString.length()) {
                // El Nombre Real es la subcadena que sigue al ':'
                realName = remainingString.substring(firstColonIndex + 1).trim(); 
            } else {
                 // Si el ':' no se encontró o está al final, el nombre real es "N/A"
                 realName = "N/A - No se pudo aislar el Nombre Real.";
            }


            // Crear y rellenar el objeto IRCUser
            IRCUser ircUser = new IRCUser(nick);
            ircUser.setUser(user);
            ircUser.setHost(host);
            ircUser.setUserHost(user + "@" + host); 
            ircUser.setFlags(flags);        
            ircUser.setServer(server);      
            ircUser.setRealName(realName); 
            ircUser.setChannel(channel);

            return ircUser;
            
        } catch (Exception e) {
            // En caso de error inesperado, loguear para diagnóstico.
            log.error("Error FATAL al parsear respuesta WHO (352): {}", response, e);
            return null;
        }
    }
    
}