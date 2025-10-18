package irc;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
// ELIMINADO: import org.jibble.pircbot.DccFileTransfer; // Ya no es necesario
import org.jibble.pircbot.PircBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ELIMINADO: import dcc.TransferManager; // Ya no es necesario
import java_irc_chat_client.Canal;
import java_irc_chat_client.CanalController;
import java_irc_chat_client.ChatController;
import javafx.application.Platform;


/**
 * ChatBot extiende PircBot 1.5.0 y maneja la conexión, eventos IRC y DCC,
 * fusionando toda la lógica de la antigua IrcEventListener.
 */
public class ChatBot extends PircBot {
    private static final Logger log = LoggerFactory.getLogger(ChatBot.class);
    private final ChatController mainController;
    // ELIMINADO: private final TransferManager TransferManager; // Ya no es necesario
    
 // --- CAMPOS PARA LA LISTA DE CANALES ---
    private Consumer<Canal> currentListReceiver;
    private Runnable currentListEndCallback;
    private boolean isListingChannels = false;
    
    private static final String NICKNAME = "akkiles4321";
    
    
 // ELIMINADO: private static final int DCC_TIMEOUT_MS = 120000; // Ya no es necesario

    // ⭐ CONSTRUCTOR SIMPLIFICADO: Eliminamos TransferManager de los argumentos
 // 📄 irc/ChatBot.java

 // 📄 irc/ChatBot.java

    public ChatBot(ChatController controller, String nickname) {
        this.mainController = controller;
        this.setName(nickname);
        this.setVerbose(true); 

        // ✅ CORRECCIÓN: Llama a los métodos protected/internal aquí
        this.setLogin(nickname); // Asigna el login (ident)
        
        // ⭐ CAMBIO CLAVE: Reemplaza setRealName() por setFinger()
        this.setFinger("JIRCHAT Client"); // Esto a menudo funciona como el "real name" o información WHOIS
        
        this.setAutoNickChange(true);
        
        log.info("🤖 ChatBot (PircBot 1.5.0) inicializado con nick: {}", nickname);
    }
    // ... resto de la clase
    // ... resto de la clase
    
    // ==========================================================
    // MANEJO DE DCC (ELIMINADO)
    // ==========================================================
    
    // ELIMINADO: @Override protected void onIncomingFileTransfer(DccFileTransfer transfer) { ... }
    // ELIMINADO: public void sendFile(String nick, File file) { ... } 
    
    // Si queremos evitar que PircBot intente manejar DCC, podemos anular el método
    // pero dejarlo vacío para un manejo más limpio, aunque PircBot ya lo hace por defecto
    // si no se sobrescribe. Lo dejamos sin sobrescribir para usar el comportamiento base (ignorar DCC).

    // ==========================================================
    // EVENTOS DE CONEXIÓN Y DESCONEXIÓN (Sin cambios)
    // ==========================================================

    @Override
    protected void onConnect() {
        mainController.setConnected(true);
        log.info("✅ Conexión IRC establecida con {}", getServer());
        
        Platform.runLater(() -> {
            mainController.getInputField().setDisable(false);
            mainController.appendSystemMessage("✅ Conectado al servidor: " + getServer());
        });

        // Identificación con NickServ
        String password = mainController.getPassword();
        if (password != null && !password.isEmpty()) {
            this.sendMessage("NickServ", "IDENTIFY " + password);
            log.info("🔐 Enviando identificación a NickServ");
        }

        // Unirse a canales automáticamente (después de una pequeña pausa)
        new Thread(() -> {
            try {
                Thread.sleep(2000); 
                String[] canales = {"#tester", "#chat"};
                for (String canal : canales) {
                    this.joinChannel(canal);
                    log.info("🔹 Solicitando unión a canal: {}", canal);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⏹️ Hilo de unión a canales interrumpido");
            }
        }).start();
    }
    
    @Override
    protected void onDisconnect() {
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
    // EVENTOS DE MENSAJE Y NOTIFICACIÓN (Sin cambios)
    // ==========================================================

    @Override
    protected void onMessage(String channel, String sender, String login, String hostname, String message) {
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(channel);

        if (ventanaWrapper != null) {
            Platform.runLater(() -> {
                ventanaWrapper.controller.appendMessage(sender, message);
            });
            log.info("Mensaje en {}: <{}> {}", channel, sender, message);
        }
    }
    
    @Override
    protected void onPrivateMessage(String sender, String login, String hostname, String message) {
        // PircBot solo dispara esto para mensajes de texto (no DCC).
        Platform.runLater(() -> mainController.onPrivateMessageRemoto(sender, message));
    }

    @Override
    protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String message) {
        String source = sourceNick != null ? sourceNick : getServer();

        log.info("[NOTICE de {}] {}", source, message);

        // Lógica Anti-Bot (igual que en PircBotX)
        if (message.contains("sum") || message.contains("calcula") || message.contains("resultado de") || message.contains("what is")) {
            log.warn("⚠️ Mensaje Anti-Bot (NOTICE) detectado: {}", message);
            String respuesta = parseAndSolveBotQuiz(message);
            
            if (respuesta != null) {
                Platform.runLater(() -> mainController.appendSystemMessage("🤖 Intentando responder Anti-Bot (NOTICE) con: " + respuesta));
                this.sendRawLine(respuesta); // PircBot usa sendRawLine
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
    // EVENTOS DE CANAL Y USUARIO (Sin cambios)
    // ==========================================================

 // 📄 irc/ChatBot.java (onJoin - NO NECESITA CAMBIOS ADICIONALES)

    @Override
    protected void onJoin(String channel, String sender, String login, String hostname) {
        // Lógica de unir/abrir ventana si eres tú
        if (sender.equals(getNick())) {
            mainController.appendSystemMessage("🚪 Has entrado al canal " + channel);
        } 
        
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(channel);
        if (ventanaWrapper != null) {
            Platform.runLater(() -> {
                // Notificar la unión
                ventanaWrapper.controller.appendSystemMessage(
                    "» " + sender + " se ha unido a " + channel, 
                    CanalController.MessageType.JOIN, 
                    sender
                );
                // ⭐ CLAVE: Este es el paso que le pide al servidor la lista actualizada.
                this.sendRawLine("NAMES " + channel); 
            });
        }
    }
 

 /**
  * Sobrescribe para recibir la lista de usuarios de un canal (después de JOIN o NAMES).
  */
 @Override
 protected void onUserList(String channel, org.jibble.pircbot.User[] users) {
     // 1. Convertir el array de User a una lista de Strings con el prefijo
     List<String> userNicks = new ArrayList<>();
     for (org.jibble.pircbot.User user : users) {
         // PircBot ya incluye el prefijo (@, +, etc.) en el nick si existe
         userNicks.add(user.toString()); 
     }
     
     // 2. Ejecutar la actualización en el hilo de JavaFX
     Platform.runLater(() -> {
         // Enviar la lista completa al ChatController para que actualice la ventana
         mainController.actualizarUsuariosCanal(channel, userNicks);
     });
     
     log.info("Lista de usuarios recibida para {}. Total: {}", channel, userNicks.size());
 }

    @Override
    protected void onPart(String channel, String sender, String login, String hostname) {
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(channel);
        
        if (sender.equals(getNick())) {
            mainController.cerrarCanalDesdeVentana(channel);
            mainController.appendSystemMessage("« Has salido del canal " + channel);
        } else if (ventanaWrapper != null) {
            Platform.runLater(() -> {
                String mensaje = "« " + sender + " ha salido de " + channel;
                ventanaWrapper.controller.appendSystemMessage(
                    mensaje, 
                    CanalController.MessageType.PART, 
                    sender
                );
                // Pedimos la lista actualizada de NAMES
                this.sendRawLine("NAMES " + channel); 
            });
        }
    }

    @Override
    protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
        Platform.runLater(() -> {
            mainController.appendSystemMessage("« " + sourceNick + " ha abandonado IRC (" + reason + ")");
            // PircBot no tiene un método directo para actualizar todas las listas,
            // por lo que tendremos que iterar sobre los canales abiertos y pedir NAMES.
            for (String canal : mainController.getCanalesAbiertos().keySet()) {
                this.sendRawLine("NAMES " + canal); 
            }
        });
    }

    @Override
    protected void onNickChange(String oldNick, String login, String hostname, String newNick) {
        Platform.runLater(() -> {
            mainController.appendSystemMessage("↔️ " + oldNick + " ahora es conocido como " + newNick);
            
            // Actualizar listas de usuarios en todos los canales
            for (String canal : mainController.getCanalesAbiertos().keySet()) {
                this.sendRawLine("NAMES " + canal); 
            }
        });
    }

    
    
 // --- SOBREESCRITURA PARA PROCESAR LA RESPUESTA LIST (322, 323) (Sin cambios)---

 

    @Override
    protected void onServerResponse(int code, String response) {
        super.onServerResponse(code, response); 

        if (!isListingChannels) return;

        if (code == 322) { // Respuesta LIST (RPL_LIST): Datos de un canal
            
            System.out.println("------------------------------------------------------------------");
            System.out.println(">>> RESPUESTA 322 COMPLETA (ORIGINAL): " + response); 
            
            try {
                String channelName = "";
                int userCount = 0;
                String modos = "";
                String topic = "";
                
                // ⭐⭐⭐ AJUSTE CRÍTICO: ANCLAJE AL NICK DEL BOT ⭐⭐⭐
                // Reemplaza "akkiles4321" con una variable si el nick puede cambiar.
                final String BOT_NICK = "akkiles4321"; 
                
                int startOfDataIndex = response.indexOf(BOT_NICK);
                
                if (startOfDataIndex == -1) {
                    System.err.println("Error de parseo: No se pudo anclar al nick del bot (" + BOT_NICK + ").");
                    return;
                }
                
                // La parte de la respuesta comienza con el Nick
                String dataPart = response.substring(startOfDataIndex).trim();

                // Dividimos la parte de datos en 5 tokens como máximo:
                // [Nick] [#Channel] [Users] [Modes] [Topic] (todo el resto)
                String[] tokens = dataPart.split(" ", 5); 
                
                // --- 2. EXTRACCIÓN USANDO ÍNDICES FIJOS DE dataPart ---
                
                // A. Nombre del Canal (tokens[1])
                if (tokens.length >= 2) { 
                    channelName = tokens[1];
                }
                
                // B. Conteo de Usuarios (tokens[2])
                if (tokens.length >= 3) {
                    try {
                        userCount = Integer.parseInt(tokens[2]);
                    } catch (NumberFormatException ignored) { }
                }
                
                // C. Modos (tokens[3])
                if (tokens.length >= 4) {
                    modos = tokens[3]; 
                    
                    // Limpieza de Modos: Quita el ':' inicial
                    if (modos.startsWith(":")) {
                        modos = modos.substring(1);
                    }
                }
                
                // D. Topic/Descripción (tokens[4])
                if (tokens.length >= 5) {
                    topic = tokens[4];
                    
                    // Limpieza del Topic: Puede contener un ':' al inicio que debe ser quitado
                    if (topic.startsWith(":")) {
                        topic = topic.substring(1).trim();
                    }
                } else {
                     topic = ""; // No hay topic
                }
                
                // ⭐⭐⭐ IMPRESIÓN DE TOKENS PARSEADOS ⭐⭐⭐
                System.out.println("PARSING EXITOSO:");
                System.out.println("  Token 1 (Canal): " + channelName);
                System.out.println("  Token 2 (Usuarios): " + userCount);
                System.out.println("  Token 3 (Modos): " + modos);
                System.out.println("  Descripción (Topic - Token 4): " + (topic.length() > 60 ? topic.substring(0, 60) + "..." : topic)); 
                System.out.println("------------------------------------------------------------------");

                // 3. Crear y enviar el objeto Canal
                Canal canal = new Canal(channelName, userCount, modos, topic);
                
                if (currentListReceiver != null && !channelName.isEmpty()) {
                    currentListReceiver.accept(canal);
                }
                
            } catch (Exception e) {
                System.err.println("Error FATAL al parsear respuesta 322: " + response + " (" + e.getMessage() + ")");
                e.printStackTrace(); 
                System.out.println("------------------------------------------------------------------");
            }

        } else if (code == 323) { // Fin de lista
            this.isListingChannels = false;
            if (currentListEndCallback != null) {
                currentListEndCallback.run();
            }
            this.currentListReceiver = null;
            this.currentListEndCallback = null;
            
            System.out.println("⭐ Fin de la lista de canales (323).");
        }
    }
    
    @Override
    protected void onAction(String sender, String login, String hostname, String target, String action) {
        // Manejo de /me (CTCP ACTION)
        CanalVentana ventanaWrapper = mainController.getCanalesAbiertos().get(target); 
        if (ventanaWrapper != null) {
            Platform.runLater(() -> {
                ventanaWrapper.controller.appendMessage(sender, action);
            });
        }
    }
    
    // ==========================================================
    // MÉTODOS AUXILIARES (Sin cambios)
    // ==========================================================

    private String parseAndSolveBotQuiz(String quizMessage) {
        try {
            Pattern pattern = Pattern.compile("(\\d+)\\s*([+\\-*/])\\s*(\\d+)");
            Matcher matcher = pattern.matcher(quizMessage);

            if (matcher.find()) {
                long num1 = Long.parseLong(matcher.group(1));
                String operator = matcher.group(2);
                long num2 = Long.parseLong(matcher.group(3));
                long result = 0;

                switch (operator) {
                    case "+": result = num1 + num2; break;
                    case "-": result = num1 - num2; break;
                    case "*": result = num1 * num2; break;
                    case "/": 
                        if (num2 != 0) result = num1 / num2; 
                        else return null; 
                        break;
                    default: return null;
                }
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
 // Esto es lo que CanalesListController llama:
    public void requestChannelList(Consumer<Canal> receiver, Runnable endCallback) {
        if (this.isConnected()) {
            this.currentListReceiver = receiver;
            this.currentListEndCallback = endCallback;
            this.isListingChannels = true;
            
            // ⭐ Método de PircBot 1.5.0 para enviar el comando LIST
            this.listChannels(); 
            
            // Opcional: Notificar al log o al controlador principal que se ha enviado el comando
        } else {
            // Manejar el caso de que el bot no esté conectado.
        }
    }
}