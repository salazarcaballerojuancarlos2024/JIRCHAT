package java_irc_chat_client;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.jibble.pircbot.DccFileTransfer;
// ⭐ IMPORTS DE PIRCBOT 1.5.0
// ELIMINADO: import org.jibble.pircbot.DccFileTransfer; // Ya no es necesario
import org.jibble.pircbot.PircBot;
// ELIMINADO: import org.jibble.pircbot.User; // No es estrictamente necesario aquí
import irc.CanalVentana;
import irc.ChatBot; 
// ELIMINADO: import dcc.TransferManager; 
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dcc.DccTransferController;


// ⭐ YA NO EXTIENDE NINGUNA CLASE DE EVENTOS DE IRC
public class ChatController {

    @FXML private TextField inputField;
    @FXML private ListView<String> userListView_canal;
    @FXML private ScrollPane chatScroll;
    @FXML private TextArea  chatFlow;
    @FXML private TextArea chatArea;
    
    // Configuraciones
    // ELIMINADO: private static final int START_PORT = 40000;
    // ELIMINADO: private static final int END_PORT = 41000;
    //private static final String NICKNAME = "akkiles4321";
    private static final String NICKNAME = "Sakkiles4321";
    private static final String SERVER = "irc.example.org";
    private static final int PORT = 6667; 
    private Stage stagePrincipal;

    // Paneles
    private StackPane rightPane;
    private VBox leftPane;
    private AnchorPane rootPane;

    // ⭐ REEMPLAZO: Usamos nuestra clase ChatBot (que extiende PircBot 1.5.0)
    private ChatBot bot; 
    private String canalActivo = null;

    public final Map<String, CanalVentana> canalesAbiertos = new HashMap<>();
    private final Map<String, Button> canalButtons = new HashMap<>();
    private final List<Stage> floatingWindows = new ArrayList<>();

    private final Map<String, Stage> privateChats = new HashMap<>();
    private final Map<String, PrivadoController> privateChatsController = new HashMap<>();
    private final Map<String, Button> privadoButtons = new HashMap<>();

    private final PauseTransition resizePause = new PauseTransition(Duration.millis(250));
    private Stage lastFocusedWindow = null;

    private String password;
    // ELIMINADO: private TransferManager transferManager; // Ya no es necesario
    private boolean isConnected = false;
    
    private static final Logger log = LoggerFactory.getLogger(ChatController.class); 

    // Solicitudes de chat pendientes o aceptadas
    private final Map<String, Boolean> solicitudPendiente = new HashMap<>();

    // --- Getters & Setters ---
    public void setBot(ChatBot bot) { this.bot = bot; }
    public ChatBot getBot() { return bot; } 

    public void setPassword(String password) { this.password = password; }
    public void setLeftPane(VBox leftPane) { this.leftPane = leftPane; }
    public void setRightPane(StackPane rightPane) { this.rightPane = rightPane; attachRightPaneSizeListeners(); }
    public void setRootPane(AnchorPane rootPane) { this.rootPane = rootPane; }
    
    public Pane getRootPane() { return rootPane; }
    public String getPassword() { return password; }
    public TextField getInputField() { return inputField; }
    public Map<String, CanalVentana> getCanalesAbiertos() { return canalesAbiertos; }
    public Map<String, PrivadoController> getPrivateChatsController() { return privateChatsController; }
    // ELIMINADO: public DccManager getDccManager() { return dccManager; } // Ya no es necesario
    public void setConnected(boolean connected) { this.isConnected = connected; }
    
    private final ObservableList<String> canalesUnidos = FXCollections.observableArrayList();
    private CanalesListController canalesListController;

 // En tu ChatController (o MainController)
    
    public void setCanalesListController(CanalesListController controller) {
        this.canalesListController = controller;
        
        // Opcional: Asegúrate de que el CanalesListController también tenga una referencia al bot o al MainController si lo necesita
        // if (bot != null) controller.setBot(bot); 
    }
    
 // En ChatController.java

    /**
     * Devuelve la Stage principal (ventana) de la aplicación.
     */
    public Stage getStagePrincipal() {
        return this.stagePrincipal;
    }

    public void agregarCanalAbierto(String channelName) throws IOException {
        // 1. Evitar duplicados en la lista de canales unidos
        if (!canalesUnidos.contains(channelName)) {
            canalesUnidos.add(channelName);
        }

        // 2. Comprobar si la ventana del canal ya está abierta
        if (canalesAbiertos.containsKey(channelName.toLowerCase())) {
            CanalVentana existente = canalesAbiertos.get(channelName.toLowerCase());
            if (existente != null && existente.stage != null) {
                existente.stage.toFront();
            }
            return;
        }

        // 3. Crear y cargar la nueva ventana (Stage) del canal
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_CANAL.fxml"));
            Parent root = loader.load();
            
            CanalController canalController = loader.getController();

            // 4. Configurar el controlador y la lógica de negocio
            canalController.setCanal(channelName);
            canalController.setBot(this.bot); 
            canalController.setMainController(this);

            // REGISTRAR EL DELEGADO DE NOMBRES
            if (this.bot != null) {
                this.bot.registerNamesDelegate(channelName, canalController); 
            }
            
            // 5. Crear la ventana (Stage)
            Stage stage = new Stage();
            stage.setTitle(channelName + " - " + bot.getName());
            stage.setScene(new Scene(root));

            // 6. Almacenar la ventana y el controlador en el mapa
            CanalVentana nuevoWrapper = new CanalVentana(stage, canalController);
            canalesAbiertos.put(channelName.toLowerCase(), nuevoWrapper);
            
            // ⭐ CONFIGURACIÓN CLAVE: Evento de cierre (Cerrar con 'X')
            stage.setOnCloseRequest(event -> {
                // 1. Enviar el comando PART (Abandonar el canal en el servidor)
                if (this.bot != null) {
                    // Notifica al servidor que el usuario abandona el canal
                    this.bot.partChannel(channelName);
                    // Notifica al usuario en la consola principal
                    appendSystemMessage("👋 Has abandonado el canal " + channelName); 
                }
                
                // 2. Limpieza interna (Desregistro del delegado y eliminación del mapa)
                if (this.bot != null) {
                    // Elimina el delegado para detener el manejo de eventos de nombres
                    this.bot.registerNamesDelegate(channelName, null); 
                }
                
                // 3. Limpiar el registro interno de canales abiertos
                cerrarCanalDesdeVentana(channelName); 
                
                // Nota: El evento no se consume (event.consume()), permitiendo que la Stage se cierre
            });
            
            // 7. Mostrar la ventana y lógica condicional JOIN/NAMES
            stage.show();

            // LÓGICA CONDICIONAL JOIN/NAMES
            if (this.bot != null) {
                if (this.bot.isJoined(channelName)) {
                    // Si ya estamos unidos (ej. canales por defecto), solo pedimos la lista de usuarios.
                    this.bot.sendRawLine("NAMES " + channelName); 
                } else {
                    // Si no estamos unidos, nos unimos. La respuesta onJoin del bot
                    // se encargará de enviar el NAMES.
                    this.bot.joinChannel(channelName); 
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error al intentar abrir la ventana del canal " + channelName + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @FXML
    public void initialize() {
        if (inputField != null) {
            inputField.setDisable(true);
            inputField.setOnAction(e -> sendCommand());
            inputField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.TAB) event.consume();
            });
        }

        resizePause.setOnFinished(ev -> {
            if (lastFocusedWindow != null) Platform.runLater(() -> lastFocusedWindow.toFront());
        });
    }
    
    public void forzarCanalUnidoEnLista(String channelName) {
        if (canalesListController != null) {
            
            // Crear un objeto 'Canal' con datos mínimos para la TableView
            // Nota: Asume que tienes un constructor Canal(String nombre, int usuarios, String modos, String descripcion)
            Canal canalObj = new Canal(channelName, 0, "+nt", "Canal unido automáticamente.");
            
            // Llama al método en el controlador de la lista para añadirlo
            canalesListController.añadirCanalForzado(canalObj); 
        }
    }

    // ------------------ COMANDOS ------------------
    private void sendCommand() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || bot == null) return;

        try {
            if (text.startsWith("/")) handleCommand(text.substring(1).trim());
            else {
                if (canalActivo != null && canalesAbiertos.containsKey(canalActivo)) {
                    CanalVentana ventana = canalesAbiertos.get(canalActivo);
                    ventana.controller.sendMessageToChannel(text);
                } else {
                    // ⭐ ADAPTACIÓN: PircBot 1.5.0 usa sendMessage
                    bot.sendMessage("NickServ", text);
                    appendSystemMessage("[Yo -> NickServ] " + text);
                }
            }
        } finally {
            inputField.clear();
        }
    }
    
    private void handleCommand(String cmd) {
        try {
            if (cmd.startsWith("join ")) agregarCanalAbierto(cmd.substring(5).trim());
            else if (cmd.startsWith("quit")) cerrarTodo("Cerrando cliente");
            else if (bot != null) bot.sendRawLine(cmd); // ⭐ ADAPTACIÓN: PircBot 1.5.0 usa sendRawLine
        } catch (Exception e) { appendSystemMessage("⚠ Error al ejecutar comando: " + e.getMessage()); }
    }

    // ------------------ PRIVADO ------------------
    public void abrirChatPrivado(String nick) {
        if (privateChats.containsKey(nick)) {
            privateChats.get(nick).toFront();
            return;
        }
        abrirPrivado(nick); // abre directamente
    }

    public void abrirChatPrivadoConMensaje(String nick, String mensaje) {
        if (privateChats.containsKey(nick)) {
            privateChats.get(nick).toFront();
            appendPrivateMessage(nick, mensaje, false);
            return;
        }
        mostrarSolicitudPrivado(nick, mensaje);
    }

    private void mostrarSolicitudPrivado(String nick, String mensaje) {
        if (Boolean.TRUE.equals(solicitudPendiente.get(nick))) {
            if (!mensaje.isEmpty()) appendPrivateMessage(nick, mensaje, false);
            return;
        }

        solicitudPendiente.put(nick, false);

        Stage solicitudStage = new Stage();
        solicitudStage.setTitle("Solicitud de chat privado de " + nick);

        VBox vbox = new VBox(10);
        vbox.setStyle("-fx-padding: 10;");
        javafx.scene.control.Label label = new javafx.scene.control.Label(
                nick + " quiere chatear contigo.\nMensaje: " + mensaje
        );
        javafx.scene.control.Button aceptarBtn = new javafx.scene.control.Button("Aceptar");
        javafx.scene.control.Button rechazarBtn = new javafx.scene.control.Button("Rechazar");

        vbox.getChildren().addAll(label, aceptarBtn, rechazarBtn);
        solicitudStage.setScene(new Scene(vbox));

        Window owner = (rootPane != null && rootPane.getScene() != null) ? rootPane.getScene().getWindow() : null;
        if (owner != null) solicitudStage.initOwner(owner);

        solicitudStage.show();

        aceptarBtn.setOnAction(e -> {
            solicitudStage.close();
            abrirPrivado(nick);
            solicitudPendiente.put(nick, true);
            if (!mensaje.isEmpty()) appendPrivateMessage(nick, mensaje, false);
        });

        rechazarBtn.setOnAction(e -> {
            solicitudStage.close();
            solicitudPendiente.remove(nick);
            if (bot != null) bot.sendMessage(nick, "⚠ Su solicitud de chat privado ha sido denegada."); 
        });
    }

    public void abrirPrivado(String nick) {
        try {
            if (privateChats.containsKey(nick)) {
                privateChats.get(nick).toFront();
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("JIRCHAT_PRIVADO.fxml"));
            Parent root = loader.load();
            PrivadoController controller = loader.getController();

            // --- INYECCIÓN DE DEPENDENCIAS ---
            controller.setBot(bot);
            controller.setDestinatario(nick);
            controller.setMainController(this);
            // ELIMINADO: if (dccManager != null) { controller.setDccManager(dccManager); } 

            // Crear Stage
            Stage stage = new Stage();
            stage.setTitle("Privado con " + nick);
            stage.setScene(new Scene(root));

            // Guardar referencias
            privateChats.put(nick, stage);
            privateChatsController.put(nick, controller);

            // Botón para el leftPane
            Button btn = new Button("@" + nick);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> stage.toFront());
            privadoButtons.put(nick, btn);

            if (leftPane != null) leftPane.getChildren().add(btn);

            // Registrar ventana flotante
            registerFloatingWindow(stage, () -> cerrarPrivado(nick));
            stage.setOnCloseRequest(ev -> cerrarPrivado(nick));

            stage.show();
        } catch (Exception e) {
            appendSystemMessage("⚠ Error al abrir privado con " + nick + ": " + e.getMessage());
        }
    }


    public void cerrarPrivado(String nick) {
        // Cerrar ventana y limpiar referencias
        Stage stage = privateChats.remove(nick);
        if (stage != null) stage.close();

        privateChatsController.remove(nick);

        Button btn = privadoButtons.remove(nick);
        if (btn != null && leftPane != null) leftPane.getChildren().remove(btn);

        solicitudPendiente.remove(nick);
    }


    public void appendPrivateMessage(String nick, String mensaje, boolean esMio) {
        if (esMio) return;  // Ignorar mensajes propios

        PrivadoController controller = privateChatsController.get(nick);
        if (controller != null) controller.appendMessage(nick, mensaje);
    }

    // ------------------ MENSAJES CON COLORES ------------------
    // Mensaje normal de usuario
    public void appendMessage(String usuario, String mensaje) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText("<" + usuario + "> " + mensaje + "\n");
                chatArea.positionCaret(chatArea.getLength()); // auto-scroll
            }
        });
    }

    // Mensaje del sistema
    public void appendSystemMessage(String mensaje) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText("[Sistema] " + mensaje + "\n");
                chatArea.positionCaret(chatArea.getLength()); // auto-scroll
            }
        });
    }

    // Mensaje privado
    public void appendPrivateMessage(String usuario, String mensaje) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText("<" + usuario + "> " + mensaje + "\n");
                chatArea.positionCaret(chatArea.getLength()); // auto-scroll
            }
        });
    }


    private String parseIRCMessage(String mensaje) {
        // Eliminamos los códigos de IRC de color y formato
        StringBuilder plainText = new StringBuilder();
        int i = 0;

        while (i < mensaje.length()) {
            char c = mensaje.charAt(i);
            if (c == '\u0003' || c == '\u000F' || c == '\u0002') {
                // Saltamos códigos de color, reset y negrita
                i++;
                // Para código de color, puede haber números después
                if (c == '\u0003') {
                    while (i < mensaje.length() && Character.isDigit(mensaje.charAt(i))) i++;
                }
            } else {
                plainText.append(c);
                i++;
            }
        }

        return plainText.toString();
    }
    
 // Clase ChatController.java (Método Regenerado)
    public void handleIncomingDccRequest(String senderNick, DccFileTransfer transfer) {
        // 1. Ejecutar la UI en el Hilo de JavaFX
        Platform.runLater(() -> {
            try {
                // ⭐ 1. Cargar el FXML de la nueva ventana de transferencia
                // Asegúrate de que esta ruta es correcta para tu proyecto
                FXMLLoader loader = new FXMLLoader(getClass().getResource("JIRCHAT_DccTransferWindows.fxml"));
                Parent root = loader.load();
                
                DccTransferController dccController = loader.getController();

                // 2. Configurar la Stage (Ventana No Bloqueante)
                Stage stage = new Stage();
                stage.setTitle("Transferencia DCC de " + senderNick);
                stage.setScene(new Scene(root));
                stage.initModality(Modality.NONE); // ⭐ IMPORTANTE: No bloquea la aplicación
                stage.initOwner(getStagePrincipal()); // Asume que tienes un getter para la Stage principal
                
                // 3. Inicializar el controlador de la ventana de transferencia
                // Le pasamos el Bot, la Transferencia, el Controlador principal y la Stage para el cierre.
                dccController.initializeTransfer(bot, transfer, this, stage, senderNick); 

                // 4. Mostrar la ventana de decisión (se abrirá como una ventana flotante)
                stage.show();
                
                appendSystemMessage("📬 Solicitud DCC de " + senderNick + ". ¡Revisa la ventana flotante!");

                // 5. Opcional: Integración en el panel izquierdo (si usas una lista de canales/privados)
                // Llama aquí a la lógica para añadir un "botón" DCC en tu panel lateral.
                // Ejemplo: actualizarPanelLateralDcc(senderNick, transfer.getFile().getName(), dccController);

            } catch (IOException e) {
                appendSystemMessage("❌ Error interno al abrir el diálogo de transferencia DCC.");
                log.error("Error al cargar DccTransferWindow.fxml", e);
                transfer.close();
            }
        });
    }

    /**
     * Método auxiliar para formatear el tamaño de bytes a KB/MB.
     */
    public String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " Bytes";
        int unit = 1024;
        String[] units = {"KB", "MB", "GB"};
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        return String.format("%.2f %s", bytes / Math.pow(unit, exp), units[exp - 1]);
    }


    private Color ircColorToFX(int code) {
        return switch (code) {
            case 0 -> Color.WHITE; case 1 -> Color.BLACK; case 2 -> Color.DODGERBLUE;
            case 3 -> Color.LIMEGREEN; case 4 -> Color.RED; case 5 -> Color.SADDLEBROWN;
            case 6 -> Color.MEDIUMPURPLE; case 7 -> Color.ORANGE; case 8 -> Color.GOLD;
            case 9 -> Color.GREEN; case 10 -> Color.CYAN; case 11 -> Color.TURQUOISE;
            case 12 -> Color.ROYALBLUE; case 13 -> Color.HOTPINK; case 14 -> Color.DARKGREY; case 15 -> Color.LIGHTGREY;
            default -> Color.BLACK;
        };
    }

    private void scrollToBottom(ScrollPane sp) { Platform.runLater(() -> sp.setVvalue(1.0)); }

    public void onPrivateMessageRemoto(String nick, String mensaje) {
        if (privateChats.containsKey(nick)) {
            // Si ya hay chat abierto, solo lo mostramos y añadimos el mensaje
            privateChats.get(nick).toFront();
            appendPrivateMessage(nick, mensaje, false);
        } else {
            // Solo si no hay chat abierto, mostramos la solicitud
            mostrarSolicitudPrivado(nick, mensaje);
        }
    }

    
    // ------------------ CONEXIÓN IRC ------------------

 // 📄 java_irc_chat_client/ChatController.java

    public void connectToIRC() {
        if (isConnected) {
            appendSystemMessage("ℹ️ Ya estás conectado al servidor");
            return;
        }

        new Thread(() -> {
            try {
                appendSystemMessage("🔹 Paso 1: Iniciando conexión al servidor IRC...");

                // 1. INICIALIZAR BOT
                // El constructor de ChatBot ahora maneja setLogin, setFinger/setRealName, etc.
                ChatBot newBot = new ChatBot(this, NICKNAME);

                // 2. CONFIGURACIÓN DEL BOT (API de PircBot 1.5.0)
                // ❌ ELIMINAR ESTAS LÍNEAS. YA ESTÁN EN EL CONSTRUCTOR DE ChatBot.
                // newBot.setLogin(NICKNAME);
                // newBot.setRealName("JIRCHAT"); 
                // newBot.setAutoNickChange(true);
                
                // ⭐ CRÍTICO: CONFIGURACIÓN DCC ELIMINADA (Ya hecho)
                
                // 3. ASIGNAR BOT A LA CLASE
                bot = newBot; 
                setBot(bot); 
                
                appendSystemMessage("🔹 Paso 2: Listeners configurados - conectando...");
                appendSystemMessage("🔹 Conectando a " + SERVER + ":" + PORT + "...");
                
                

                // 4. CONECTAR (API de PircBot 1.5.0)
                bot.connect(SERVER, PORT); 
                isConnected = true; 

            } catch (Exception e) {
                e.printStackTrace(); 
                // ... (manejo de errores)
            }
        }).start();
    }
    

    
   // ------------------ UTILIDAD DE RED (Simplificado) ------------------
    
    // ⭐ ELIMINADO: Ya no necesitamos la IP pública para DCC, solo la local como fallback
    private InetAddress getPublicIPAddress() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            log.error("❌ Fallback fallido: No se pudo obtener ni la IP local.", e);
            return null; 
        }
    }


    private void handleConnectionError(Exception e) {
        String errorMsg = e.getMessage().toLowerCase();
        
        if (errorMsg.contains("connection refused") || errorMsg.contains("connect timed out")) {
            appendSystemMessage("💡 El servidor no está disponible o rechazó la conexión.");
            appendSystemMessage("💡 Verifica que '" + SERVER + "' esté online.");
        } else if (errorMsg.contains("unknownhost")) {
            appendSystemMessage("💡 Servidor no encontrado: '" + SERVER + "'");
            appendSystemMessage("💡 Verifica el nombre del servidor o tu conexión a Internet.");
        } else if (errorMsg.contains("ssl") || errorMsg.contains("handshake")) {
            appendSystemMessage("💡 Error SSL. Prueba estas opciones:");
            appendSystemMessage("💡 1. Usar puerto 6667 (sin SSL)");
            appendSystemMessage("💡 2. Verificar certificados del servidor");
        } else if (errorMsg.contains("timeout")) {
            appendSystemMessage("💡 Timeout de conexión. El servidor no respondió.");
            appendSystemMessage("💡 Puede estar caído o sobrecargado.");
        } else {
            appendSystemMessage("💡 Error desconocido. Revisa los logs para más detalles.");
        }
        
        appendSystemMessage("🔍 Servidores alternativos para probar:");
        appendSystemMessage("   - irc.libera.chat:6697 (SSL)");
        appendSystemMessage("   - irc.efnet.org:6667 (sin SSL)");
    }

    // ------------------ CANALES ------------------
    
    

    public void cerrarCanalDesdeVentana(String canal) {
        CanalVentana ventana = canalesAbiertos.remove(canal);
        if (ventana != null) Platform.runLater(() -> ventana.stage.close());
        Button btn = canalButtons.remove(canal);
        if (btn != null && leftPane != null) Platform.runLater(() -> leftPane.getChildren().remove(btn));
        floatingWindows.remove(ventana.stage);
    }

    public void actualizarUsuariosCanal(String canal, List<String> usuarios) {
        CanalVentana ventana = canalesAbiertos.get(canal);
        if (ventana != null) ventana.controller.updateUsers(usuarios);
    }

    // ------------------ Ventanas flotantes (Sin cambios) ------------------
    private void attachRightPaneSizeListeners() {
        if (rightPane == null) return;
        rightPane.widthProperty().addListener((obs, oldV, newV) -> repositionFloatingWindows());
        rightPane.heightProperty().addListener((obs, oldV, newV) -> repositionFloatingWindows());
    }

    private void registerFloatingWindow(Stage stage, Runnable onCloseCleanup) {
        if (stage == null) return;
        if (!floatingWindows.contains(stage)) floatingWindows.add(stage);

        stage.focusedProperty().addListener((obs, was, now) -> { if (now) lastFocusedWindow = stage; });
        stage.setOnCloseRequest(ev -> { floatingWindows.remove(stage); try { onCloseCleanup.run(); } catch (Exception ignored) {} });

        repositionFloatingWindows();
    }

    private void repositionFloatingWindows() {
        if (rightPane == null) return;
        Window mainWin = rightPane.getScene().getWindow();
        if (mainWin == null) return;

        double rightX = mainWin.getX() + rightPane.localToScene(0,0).getX() + rightPane.getScene().getX();
        double rightY = mainWin.getY() + rightPane.localToScene(0,0).getY() + rightPane.getScene().getY();
        double width = rightPane.getWidth();
        double height = rightPane.getHeight();

        for (Stage stage : floatingWindows) {
            stage.setX(rightX);
            stage.setY(rightY);
            stage.setWidth(width);
            stage.setHeight(height);
        }
    }

    public void bindFloatingWindowsToRightPane(Stage primaryStage) {
        if (primaryStage == null) return;

        primaryStage.setOnCloseRequest(event -> {
            for (Stage s : new ArrayList<>(floatingWindows)) Platform.runLater(s::close);
            for (Stage s : privateChats.values()) Platform.runLater(s::close);
        });

        primaryStage.widthProperty().addListener((obs, oldV, newV) -> scheduleResize());
        primaryStage.heightProperty().addListener((obs, oldV, newV) -> scheduleResize());
    }

    public void scheduleResize() {
        resizePause.playFromStart();
        repositionFloatingWindows();
    }

    // ------------------ CERRAR TODO (Sin cambios) ------------------
    public void cerrarTodo(String mensaje) {
        for (String canal : new ArrayList<>(canalesAbiertos.keySet()))
            cerrarCanalDesdeVentana(canal);

        for (String nick : new ArrayList<>(privateChats.keySet()))
            cerrarPrivado(nick);

        if (bot != null) {
            try {
                if (bot.isConnected()) {
                    bot.quitServer(mensaje != null ? mensaje : "Cerrando cliente");
                }
            } catch (Exception ignored) {}
        }

        Platform.exit();
    }
    
    
}
