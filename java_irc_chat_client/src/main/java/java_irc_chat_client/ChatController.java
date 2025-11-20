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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.jibble.pircbot.DccFileTransfer;
import irc.CanalVentana;
import irc.ChatBot;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dcc.DccTransferController;

public class ChatController {

    @FXML private TextField inputField;
    @FXML private ListView<String> userListView_canal;
    @FXML private ScrollPane chatScroll;
    @FXML private TextArea  chatFlow; // Parece no usarse, usamos chatArea
    @FXML private TextArea chatArea; // Consola principal
    
    
    
    //private static final String nickname = "akkiles4321";
    //private static final String nickname = "Sakkiles4321";
    //private static final String server = "irc.example.org";
    private static final int PORT = 6667; 
    private Stage stagePrincipal;
    private final File fileSecuenciaInicio = new File(System.getProperty("user.home"), ".ircclient/secuenciadeinicio.txt");

    // Paneles
    private StackPane rightPane;
    @FXML private VBox leftPane; // Panel izquierdo para botones de ventanas
    private AnchorPane rootPane;

    // --- ESTRUCTURAS DE DATOS ---
    private ChatBot bot; 
    private String canalActivo = null;

    public final Map<String, CanalVentana> canalesAbiertos = new HashMap<>();
    private final Map<String, Button> canalButtons = new HashMap<>();
    private final List<Stage> floatingWindows = new ArrayList<>();

    private final Map<String, Stage> privateChats = new HashMap<>();
    private final Map<String, PrivadoController> privateChatsController = new HashMap<>();
    private final Map<String, Button> privadoButtons = new HashMap<>();
    
    // CAMPOS DCC
    private final Map<String, DccWindowWrapper> dccAbiertos = new HashMap<>();
    private final Map<String, Button> dccButtons = new HashMap<>();


    private final PauseTransition resizePause = new PauseTransition(Duration.millis(250));
    private Stage lastFocusedWindow = null;

    private String password;
    private boolean isConnected = false;
    
    private static final Logger log = LoggerFactory.getLogger(ChatController.class); 

    private final Map<String, Boolean> solicitudPendiente = new HashMap<>();
    private final ObservableList<String> canalesUnidos = FXCollections.observableArrayList();
    private CanalesListController canalesListController;
    
    private String nickname;
    private String server;
    private boolean secuenciaInicioActivada = false;
    
    public void setServer(String server) {
        this.server = server;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public void setSecuenciaInicioActivada(boolean activada) {
        this.secuenciaInicioActivada = activada;
    }

    public boolean isSecuenciaInicioActivada() {
        return secuenciaInicioActivada;
    }
    
    public void ejecutarSecuenciaInicio(boolean chkMarcado) {
        if (!chkMarcado || bot == null) {
            return;
        }

        if (!fileSecuenciaInicio.exists()) {
            appendSystemMessage("⚠ Archivo de secuencia de inicio no encontrado: " + fileSecuenciaInicio.getName());
            return;
        }

        try (java.util.Scanner scanner = new java.util.Scanner(fileSecuenciaInicio)) {
            appendSystemMessage("📜 Ejecutando secuencia de inicio...");
            
            while (scanner.hasNextLine()) {
                String command = scanner.nextLine().trim();
                if (!command.isEmpty() && !command.startsWith("#")) { // Ignorar líneas vacías y comentarios
                    
                    // ⭐ CLAVE: Enviar el comando directamente al bot (como si fuera un /comando)
                    // Usamos el mismo método que procesa la entrada del usuario en el TextField principal.
                    // Asumiendo que ese método es mainController.processUserInput(String command)
                    
                    // NOTA: Si no tienes un método unificado, tendrás que enviarlo directamente:
                    bot.sendRawLine(command); 
                    
                    // Si usas el método unificado:
                    // processUserInput(command); 
                    
                    appendSystemMessage("-> Enviado: " + command);
                    
                    // Opcional: Pausa para evitar flood (aunque PircBot suele manejar esto)
                    // Thread.sleep(500); 
                }
            }
            appendSystemMessage("✅ Secuencia de inicio finalizada.");
        } catch (IOException e) {
            e.printStackTrace();
            appendSystemMessage("❌ Error al leer la secuencia de inicio: " + e.getMessage());
        }
    }

    // MÉTODOS NUEVOS: Setters para recibir los datos del formulario.
    public void setnickname(String nickname) {
        this.nickname = nickname;
    }

    public void setserver(String server) {
        this.server = server;
    }
    
    // CLASE WRAPPER DCC
    public static class DccWindowWrapper {
        public final Stage stage;
        public final DccTransferController controller;

        public DccWindowWrapper(Stage stage, DccTransferController controller) {
            this.stage = stage;
            this.controller = controller;
        }
    }


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
    public void setConnected(boolean connected) { this.isConnected = connected; }
    public void setCanalesListController(CanalesListController controller) {
        this.canalesListController = controller;
    }
    public Stage getStagePrincipal() {
        return this.stagePrincipal;
    }

    // --- INICIALIZACIÓN ---
    
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

    // --- LÓGICA DE BOTONES Y VENTANAS (CANALES) ---

    public void agregarCanalAbierto(String channelName) throws IOException {
        final String key = channelName.toLowerCase();
        
        // 1. Evitar duplicados y comprobar si ya está abierta
        if (!canalesUnidos.contains(channelName)) {
            canalesUnidos.add(channelName);
        }
        if (canalesAbiertos.containsKey(key)) {
            CanalVentana existente = canalesAbiertos.get(key);
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

            if (this.bot != null) {
                this.bot.registerNamesDelegate(channelName, canalController); 
            }
            
            // 5. Crear la ventana (Stage)
            Stage stage = new Stage();
            stage.setTitle(channelName + " - " + bot.getName());
            stage.setScene(new Scene(root));

            // 6. Almacenar la ventana y el controlador en el mapa
            CanalVentana nuevoWrapper = new CanalVentana(stage, canalController);
            canalesAbiertos.put(key, nuevoWrapper);
            
            // ⭐ INTEGRACIÓN DEL BOTÓN EN EL PANEL IZQUIERDO
            agregarBotonCanalPrivadoDcc(channelName, stage, "canal");
            
            // ⭐ CONFIGURACIÓN CLAVE: Evento de cierre (Cerrar con 'X')
            stage.setOnCloseRequest(event -> {
                if (this.bot != null) {
                    this.bot.partChannel(channelName);
                    appendSystemMessage("👋 Has abandonado el canal " + channelName); 
                    this.bot.registerNamesDelegate(channelName, null); 
                }
                // Limpiar el botón y las referencias
                removerBotonCanal(channelName);
                cerrarCanalDesdeVentana(channelName); 
            });
            
            // 7. Mostrar la ventana y lógica condicional JOIN/NAMES
            stage.show();

            // LÓGICA CONDICIONAL JOIN/NAMES
            if (this.bot != null) {
                // Si ya estamos unidos (estado registrado por onJoin), solo pedimos la lista de usuarios
                if (this.bot.isJoined(channelName)) { 
                    this.bot.sendRawLine("NAMES " + channelName); 
                } else {
                    // Si no estamos unidos (es un /join manual y NO es un canal de autounión), nos unimos
                    this.bot.joinChannel(channelName); 
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error al intentar abrir la ventana del canal " + channelName + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
 // Nuevo método a añadir en ChatController.java

    /**
     * Crea el botón del canal en el leftPane y registra el canal, 
     * pero NO carga el FXML ni abre el Stage. Usado para canales de autounión.
     */
    public void registerAndCreateButton(String channelName) {
        final String key = channelName.toLowerCase();
        
        // 1. Evitar duplicados
        if (canalesAbiertos.containsKey(key) || canalButtons.containsKey(key)) {
            return;
        }
        if (!canalesUnidos.contains(channelName)) {
            canalesUnidos.add(channelName);
        }

        // 2. Crear un Stage (Ventana) ficticio y un controlador nulo para registrar la referencia.
        // Necesitas este Stage Ficticio y CanalVentana Ficticia para que las otras partes 
        // de la lógica del ChatController no fallen al buscar 'Stage'.
        Stage stageFicticio = new Stage(); // Este Stage nunca se mostrará.
        
        // ⭐ IMPORTANTE: No podemos crear el CanalVentana porque requiere el CanalController real.
        // La forma más segura de evitar errores es NO registrarlo en canalesAbiertos si no hay ventana.
        // Pero, si no se registra, las funciones de NAMES no funcionarán si el usuario lo abre después.
        
        // En su lugar, asumiremos que si el usuario hace clic en el botón, se abre la ventana
        // utilizando la lógica de agregarCanalAbierto.
        
        // Por lo tanto, solo creamos y añadimos el botón:
        
        // 3. Integración del botón en el panel izquierdo
        Button btn = new Button(channelName);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            try {
                // Cuando el usuario hace clic por primera vez, abrimos la ventana real.
                // Si el canal ya está en el mapa, simplemente lo trae al frente (lógica de agregarCanalAbierto).
                agregarCanalAbierto(channelName);
            } catch (java.io.IOException ex) {
                appendSystemMessage("⚠ Error al abrir ventana del canal " + channelName);
            }
        });
        btn.getStyleClass().add("canal-button"); 

        // 4. Almacenar la referencia del botón
        canalButtons.put(key, btn);

        // 5. Añadir el botón al panel izquierdo
        if (leftPane != null) Platform.runLater(() -> leftPane.getChildren().add(btn));
    }
    
    public void removerBotonCanal(String channelName) {
        // Usar toLowerCase para coincidir con la clave del mapa
        final String key = channelName.toLowerCase(); 
        
        // 1. Remover el botón de la referencia del mapa
        Button btn = canalButtons.remove(key); 
        
        // 2. Remover el botón de la GUI (leftPane)
        if (btn != null && leftPane != null) {
            // Ejecutar en el hilo de JavaFX
            Platform.runLater(() -> leftPane.getChildren().remove(btn));
        }
    }

    public void cerrarCanalDesdeVentana(String canal) {
        // La lógica de cierre en el stage.setOnCloseRequest ya maneja el PART y la remoción del botón.
        CanalVentana ventana = canalesAbiertos.remove(canal.toLowerCase());
        if (ventana != null) Platform.runLater(() -> ventana.stage.close());
        floatingWindows.remove(ventana.stage);
    }
    
    // --- LÓGICA DE BOTONES Y VENTANAS (PRIVADOS) ---

    public void abrirPrivado(String nick) {
        final String key = nick.toLowerCase();
        
        try {
            if (privateChats.containsKey(key)) {
                privateChats.get(key).toFront();
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("JIRCHAT_PRIVADO.fxml"));
            Parent root = loader.load();
            PrivadoController controller = loader.getController();

            // --- INYECCIÓN DE DEPENDENCIAS ---
            controller.setBot(bot);
            controller.setDestinatario(nick);
            controller.setMainController(this);

            // Crear Stage
            Stage stage = new Stage();
            stage.setTitle("Privado con " + nick);
            stage.setScene(new Scene(root));

            // Guardar referencias
            privateChats.put(key, stage);
            privateChatsController.put(key, controller);

            // ⭐ INTEGRACIÓN DEL BOTÓN EN EL PANEL IZQUIERDO
            agregarBotonCanalPrivadoDcc(nick, stage, "privado");

            // Registrar ventana flotante
            registerFloatingWindow(stage, () -> cerrarPrivado(nick));
            stage.setOnCloseRequest(ev -> cerrarPrivado(nick)); // Esta limpieza ya remueve el botón

            stage.show();
        } catch (Exception e) {
            appendSystemMessage("⚠ Error al abrir privado con " + nick + ": " + e.getMessage());
        }
    }


    public void cerrarPrivado(String nick) {
        final String key = nick.toLowerCase();
        
        // Cerrar ventana y limpiar referencias
        Stage stage = privateChats.remove(key);
        if (stage != null) stage.close();

        privateChatsController.remove(key);

        // REMOCIÓN DEL BOTÓN PRIVADO
        Button btn = privadoButtons.remove(key);
        if (btn != null && leftPane != null) Platform.runLater(() -> leftPane.getChildren().remove(btn));

        solicitudPendiente.remove(key);
    }
    
    // --- LÓGICA DE BOTONES Y VENTANAS (DCC) ---

    public void handleIncomingDccRequest(String senderNick, DccFileTransfer transfer) {
        Platform.runLater(() -> {
            final String key = senderNick.toLowerCase(); 
            
            try {
                // Si ya hay una ventana DCC abierta con este nick, no abrimos otra.
                if (dccAbiertos.containsKey(key)) {
                    dccAbiertos.get(key).stage.toFront();
                    return;
                }
                
                FXMLLoader loader = new FXMLLoader(getClass().getResource("JIRCHAT_DccTransferWindows.fxml"));
                Parent root = loader.load();
                
                DccTransferController dccController = loader.getController();

                Stage stage = new Stage();
                stage.setTitle("Transferencia DCC de " + senderNick);
                stage.setScene(new Scene(root));
                stage.initModality(Modality.NONE); 
                stage.initOwner(getStagePrincipal());
                
                dccController.initializeTransfer(bot, transfer, this, stage, senderNick); 

                // PASO 1: Registrar la ventana DCC
                DccWindowWrapper dccWrapper = new DccWindowWrapper(stage, dccController);
                dccAbiertos.put(key, dccWrapper); 

                // PASO 2: Crear y añadir el botón en el panel izquierdo
                agregarBotonCanalPrivadoDcc(senderNick, stage, "dcc"); 

                // PASO 3: Configurar la limpieza al cerrar la ventana con la 'X'
                stage.setOnCloseRequest(event -> {
                    // Ejecutar la limpieza de DCC (eliminar botón y referencia)
                    removerBotonDcc(key);
                    transfer.close(); // Asegurarse de cerrar la transferencia
                });

                stage.show();
                
                appendSystemMessage("📬 Solicitud DCC de " + senderNick + ". ¡Revisa la ventana flotante!");

            } catch (IOException e) {
                appendSystemMessage("❌ Error interno al abrir el diálogo de transferencia DCC.");
                log.error("Error al cargar DccTransferWindow.fxml", e);
                transfer.close();
            }
        });
    }

 // En ChatController.java

    public void removerBotonDcc(String nickKey) {
        final String key = nickKey.toLowerCase();
        
        // 1. Remover el botón de la GUI
        Button btn = dccButtons.remove(key);
        if (btn != null && leftPane != null) Platform.runLater(() -> leftPane.getChildren().remove(btn));
        
        // 2. Remover el wrapper del mapa de control (Stage/Controller)
        DccWindowWrapper wrapper = dccAbiertos.remove(key);
        
        // 3. Cierra la ventana si aún no está cerrada.
        if (wrapper != null && wrapper.stage != null) {
            Platform.runLater(() -> {
                if (wrapper.stage.isShowing()) {
                    wrapper.stage.close();
                }
            });
        }
    }
    
    // --- LÓGICA CENTRAL DE BOTONES (Nueva) ---

    private void agregarBotonCanalPrivadoDcc(String name, Stage stage, String tipo) {
        final String key = name.toLowerCase();
        
        String text = switch (tipo) {
            case "canal" -> name;
            case "privado" -> "@" + name;
            case "dcc" -> "📥 DCC: " + name;
            default -> name;
        };
        
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> stage.toFront());
        btn.getStyleClass().add(tipo + "-button"); // Para estilizar con CSS

        // Almacenar la referencia en el mapa correcto
        switch (tipo) {
            case "canal":
                canalButtons.put(key, btn);
                break;
            case "privado":
                privadoButtons.put(key, btn);
                break;
            case "dcc":
                dccButtons.put(key, btn);
                break;
        }

        // Añadir el botón al panel izquierdo
        if (leftPane != null) leftPane.getChildren().add(btn);
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
                    // Si no hay canal activo, enviamos a NickServ
                    bot.sendMessage("NickServ", text);
                    appendSystemMessage("[Yo -> NickServ] " + text);
                }
            }
        } finally {
            inputField.clear();
        }
    }
    
 // Dentro de ChatController.java

    private void handleCommand(String cmd) {
        try {
            if (cmd.startsWith("join ")) {
                agregarCanalAbierto(cmd.substring(5).trim());
            } else if (cmd.startsWith("quit")) {
                // ⭐ CAMBIO CRÍTICO: Manejamos la desconexión sin cerrar la aplicación.
                handleQuitOnly(cmd.substring(4).trim()); 
                
            } else if (bot != null) {
                // Si no es join ni quit, lo enviamos crudo.
                bot.sendRawLine(cmd); 
            }
        } catch (Exception e) { 
            appendSystemMessage("⚠ Error al ejecutar comando: " + e.getMessage()); 
        }
    }
 // Dentro de ChatController.java (Método Nuevo)

    /**
     * Desconecta el bot del servidor y limpia la UI relacionada con la conexión, 
     * pero mantiene la aplicación principal abierta.
     */
    private void handleQuitOnly(String mensaje) {
        if (bot != null && bot.isConnected()) {
            
            String quitMessage = mensaje.isEmpty() ? "Desconexión solicitada por el usuario" : mensaje;
            
            // 1. Desconectar el bot del servidor
            bot.quitServer(quitMessage); 
            
            // 2. Limpiar la UI relacionada con los canales, privados y DCCs
            // (La lógica es similar a cerrarTodo, pero sin cerrar la Stage principal)

            for (String canal : new ArrayList<>(canalesAbiertos.keySet()))
                cerrarCanalDesdeVentana(canal);

            for (String nick : new ArrayList<>(privateChats.keySet()))
                cerrarPrivado(nick);
                
            for (String nick : new ArrayList<>(dccAbiertos.keySet()))
                removerBotonDcc(nick);

            // La función onDisconnect() del ChatBot debería manejar la actualización final de la UI (deshabilitar inputField, etc.)
            appendSystemMessage("🔌 Desconectado del servidor por comando /quit.");
            
        } else {
            appendSystemMessage("⚠️ Ya estás desconectado. El comando /quit no tiene efecto.");
        }
    }

    // ------------------ PRIVADO (AUXILIARES) ------------------
    public void abrirChatPrivado(String nick) {
        if (privateChats.containsKey(nick)) {
            privateChats.get(nick).toFront();
            return;
        }
        abrirPrivado(nick);
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

    public void appendPrivateMessage(String nick, String mensaje, boolean esMio) {
        if (esMio) return;

        PrivadoController controller = privateChatsController.get(nick.toLowerCase());
        if (controller != null) controller.appendMessage(nick, mensaje);
    }
    
    public void appendPrivateMessage(String usuario, String mensaje) {
        // Método que usa el PircBot Listener para pasar mensajes privados a la consola principal
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText("<" + usuario + "> " + mensaje + "\n");
                chatArea.positionCaret(chatArea.getLength());
            }
        });
    }

    // ------------------ MENSAJES CON COLORES ------------------
    public void appendMessage(String usuario, String mensaje) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText("<" + usuario + "> " + mensaje + "\n");
                chatArea.positionCaret(chatArea.getLength());
            }
        });
    }
    
    public void appendSystemMessage(String mensaje) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText("[Sistema] " + mensaje + "\n");
                chatArea.positionCaret(chatArea.getLength());
            }
        });
    }

    private String parseIRCMessage(String mensaje) {
        StringBuilder plainText = new StringBuilder();
        int i = 0;

        while (i < mensaje.length()) {
            char c = mensaje.charAt(i);
            if (c == '\u0003' || c == '\u000F' || c == '\u0002') {
                i++;
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
    
    public void forzarCanalUnidoEnLista(String channelName) {
        if (canalesListController != null) {
            // Asume que Canal es una clase definida con un constructor adecuado.
            // Para fines de regeneración, asumimos la estructura.
            // Canal canalObj = new Canal(channelName, 0, "+nt", "Canal unido automáticamente.");
            // canalesListController.añadirCanalForzado(canalObj); 
        }
    }
    
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
        if (privateChats.containsKey(nick.toLowerCase())) {
            privateChats.get(nick.toLowerCase()).toFront();
            appendPrivateMessage(nick, mensaje, false);
        } else {
            mostrarSolicitudPrivado(nick, mensaje);
        }
    }
    
    // ------------------ CONEXIÓN IRC ------------------
 // Dentro de ChatController.java

 // Dentro de ChatController.java (Asegúrate de que 'server' y 'nickname' sean variables de instancia)

    public void connectToIRC() {
        // 1. Verificación: Si ya estamos conectados, no hacemos nada.
        if (isConnected) {
            appendSystemMessage("ℹ️ Ya estás conectado al servidor.");
            return;
        }

        // 2. Validación de datos esenciales
        if (server == null || server.trim().isEmpty() || nickname == null || nickname.trim().isEmpty()) {
            Platform.runLater(() -> handleConnectionError(new Exception("Servidor o Nickname no configurados.")));
            return;
        }

        // 3. ⭐ Solución: Declarar variables locales dentro del ámbito del método
        // y no reasignarlas directamente, o usar variables intermedias.
        final String serverToConnect = server; 
        final String nickToUse = nickname;
        
        // 4. Lógica de Reconexión y Creación de ChatBot (en hilo separado)
        new Thread(() -> {
            
            // ⭐ Variables FINALES locales dentro del hilo
            String finalHost = serverToConnect;
            int finalPort = 6667; 
            
            // --- PARSEO DEL SERVIDOR DENTRO DEL HILO ---
            if (serverToConnect.contains(":")) {
                String[] parts = serverToConnect.split(":");
                finalHost = parts[0]; // Se reasignan las variables locales del hilo
                if (parts.length > 1) {
                    try {
                        finalPort = Integer.parseInt(parts[1]); // Se reasignan las variables locales del hilo
                    } catch (NumberFormatException ignored) {
                        // Si el puerto no es válido, se usa 6667 por defecto
                    }
                }
            }
            
            try {
                appendSystemMessage("🔹 Paso 1: Iniciando conexión al servidor IRC...");

                // Usamos el constructor existente (controlador, nickname)
                ChatBot newBot = new ChatBot(this, nickToUse);
                
                // Reemplazamos la referencia al bot
                bot = newBot; 
                
                appendSystemMessage("🔹 Paso 2: Listeners configurados - conectando...");
                appendSystemMessage("🔹 Conectando a " + finalHost + ":" + finalPort + "...");
                
                // 5. Conectar usando las variables FINALES
                bot.connect(finalHost, finalPort); 
                
                // isConnected = true; (Debe establecerse en onConnect() del ChatBot)

            } catch (Exception e) {
                Platform.runLater(() -> handleConnectionError(e));
                log.error("Error fatal de conexión.", e);
            }
        }).start();
    }
    

    private InetAddress getPublicIPAddress() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            log.error("❌ Fallback fallido: No se pudo obtener ni la IP local.", e);
            return null; 
        }
    }


    private void handleConnectionError(Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "Error desconocido";
        
        if (errorMsg.contains("connection refused") || errorMsg.contains("connect timed out")) {
            appendSystemMessage("💡 El servidor no está disponible o rechazó la conexión.");
        } else if (errorMsg.contains("unknownhost")) {
            appendSystemMessage("💡 Servidor no encontrado: '" + server + "'");
        } else if (errorMsg.contains("ssl") || errorMsg.contains("handshake")) {
            appendSystemMessage("💡 Error SSL.");
        } else if (errorMsg.contains("timeout")) {
            appendSystemMessage("💡 Timeout de conexión.");
        } else {
            appendSystemMessage("💡 Error desconocido. Revisa los logs para más detalles.");
        }
        
        appendSystemMessage("🔍 Conexión fallida. Intenta nuevamente.");
    }

    // ------------------ CANALES (AUXILIARES) ------------------
    
    public void actualizarUsuariosCanal(String canal, List<String> usuarios) {
        CanalVentana ventana = canalesAbiertos.get(canal.toLowerCase());
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
        if (rightPane == null || rightPane.getScene() == null) return;
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
        this.stagePrincipal = primaryStage;
        if (primaryStage == null) return;

        primaryStage.setOnCloseRequest(event -> {
            for (Stage s : new ArrayList<>(floatingWindows)) Platform.runLater(s::close);
            for (Stage s : privateChats.values()) Platform.runLater(s::close);
            for (DccWindowWrapper w : dccAbiertos.values()) Platform.runLater(w.stage::close);
            
            // Llama a cerrarTodo para gestionar el bot/conexión
            cerrarTodo("Cerrando cliente"); 
        });

        primaryStage.widthProperty().addListener((obs, oldV, newV) -> scheduleResize());
        primaryStage.heightProperty().addListener((obs, oldV, newV) -> scheduleResize());
    }

    public void scheduleResize() {
        resizePause.playFromStart();
        repositionFloatingWindows();
    }
    
 // En ChatController.java

    

    // ------------------ CERRAR TODO (Sin cambios) ------------------
    public void cerrarTodo(String mensaje) {
        // Aseguramos que la Stage principal se cierre solo si está visible
        Platform.runLater(() -> {
            if (stagePrincipal != null) stagePrincipal.close();
        });
        
        for (String canal : new ArrayList<>(canalesAbiertos.keySet()))
            cerrarCanalDesdeVentana(canal);

        for (String nick : new ArrayList<>(privateChats.keySet()))
            cerrarPrivado(nick);
            
        // Limpiamos DCC
        for (String nick : new ArrayList<>(dccAbiertos.keySet()))
            removerBotonDcc(nick);

        if (bot != null) {
            try {
                if (bot.isConnected()) {
                    bot.quitServer(mensaje != null ? mensaje : "Cerrando cliente"); 
                }
            } catch (Exception ignored) {}
        }

        Platform.exit();
    }
    
 
    /**
     * Abre la ventana para listar los usuarios y sus detalles en un canal específico.
     * @param channelName El nombre del canal (#ejemplo).
     */
    public void abrirVentanaLwho(String channelName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_LWHOVIEW.fxml"));
            Parent root = loader.load();
            
            LWhoController lwhoController = loader.getController();

            // 1. Inyectar referencias necesarias
            lwhoController.setChatController(this);
            lwhoController.setBot(this.bot); // Inyectar el ChatBot
            
            // 2. Iniciar la consulta WHO
            lwhoController.iniciarConsulta(channelName); 

            // 3. Mostrar la ventana
            Stage stage = new Stage();
            stage.setTitle("Usuarios en " + channelName + " - (Cargando...)"); // Título temporal
            stage.setScene(new Scene(root));
            stage.initOwner(this.stagePrincipal); 
            stage.initModality(Modality.NONE); // No modal para permitir otras interacciones
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo abrir la ventana de Usuarios: " + e.getMessage(), ButtonType.OK);
                alert.setTitle("Error de Interfaz");
                alert.showAndWait();
            });
        }
    }
    
}
