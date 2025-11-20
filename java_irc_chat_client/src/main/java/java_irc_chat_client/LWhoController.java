package java_irc_chat_client;

import irc.ChatBot;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;

public class LWhoController {

    @FXML private Label lblChannelInfo;
    @FXML private TableView<IRCUser> userTable;
    @FXML private TableColumn<IRCUser, String> colNick;
    @FXML private TableColumn<IRCUser, String> colUserHost;
    @FXML private TableColumn<IRCUser, String> colFlags;
    @FXML private TableColumn<IRCUser, String> colServer;
    @FXML private TableColumn<IRCUser, String> colNombreReal; // Corresponde al campo RealName/Ident
    @FXML private TextFlow statusFlow;
    @FXML private Text statusText;

    private ChatBot bot;
    private ChatController chatController;
    private final ObservableList<IRCUser> users = FXCollections.observableArrayList();
    private String currentChannel;

    public void setChatController(ChatController chatController) {
        this.chatController = chatController;
    }

    public void setBot(ChatBot bot) {
        this.bot = bot;
    }

    @FXML
    public void initialize() {
        // 1. Mapeo de Propiedades (Usando los métodos Property() definidos en IRCUser)
        colNick.setCellValueFactory(data -> data.getValue().nickProperty());
        colUserHost.setCellValueFactory(data -> data.getValue().userHostProperty());
        colFlags.setCellValueFactory(data -> data.getValue().flagsProperty());
        colServer.setCellValueFactory(data -> data.getValue().serverProperty());
        colNombreReal.setCellValueFactory(data -> data.getValue().realNameProperty()); // Mapea al campo 'realName'

        userTable.setItems(users);

        // 2. Listener de Selección para mostrar el detalle en el Status
     // Dentro de LWhoController.java: en el método initialize()
     // ...
             // 2. Listener de Selección para mostrar el detalle en el Status
        userTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, user) -> {
            if (user != null) {
                
                // Formato Deseado:
                // Nick [tab] user@host [tab] Flags [tab] Servidor(0) [tab] Nombre.real.completo ( #canal)
                
                // Usamos el formato que imita el del servidor IRC para el detalle (usando '|' para separar campos)
                String singleLineDetail = String.format(
                    "Nick: %s | User@Host: %s | Flags: %s | Servidor: %s(0) | Nombre: %s ( %s )",
                    user.getNick(),           // AAngelica_ch
                    user.getUserHost(),       // androirc@i-7iq.48u.crjpc4.IP (CORREGIDO)
                    user.getFlags(),          // H@r
                    user.getServer(),         // melocoton.chatzona.org
                    user.getRealName(),       // Amarte.es.mi.mejor.decisión. (CORREGIDO)
                    currentChannel            // #el_jardin_musical
                );

                statusText.setText(singleLineDetail);
                
            } else {
                statusText.setText("Haga clic en una fila para ver la información detallada del usuario.");
            }
        });
     // ...
    }

    /**
     * Inicia la consulta WHO al servidor para el canal dado.
     * @param channelName Nombre del canal.
     */
    public void iniciarConsulta(String channelName) {
        if (bot == null || !bot.isConnected()) {
            statusText.setText("❌ Error: Bot no conectado.");
            return;
        }
        
        this.currentChannel = channelName;
        lblChannelInfo.setText("Consultando usuarios en " + channelName + "...");
        
        users.clear(); // 1. Limpiar la lista
        
        // 2. Ejecutar la consulta WHO
        // bot.requestWhoList debe enviar "WHO #canal" y guardar "this" como receptor.
        bot.requestWhoList(channelName, this); 
    }

    // -------------------------------------------------------------
    // Métodos de CALLBACK llamados por ChatBot (YA DENTRO DEL HILO FX)
    // -------------------------------------------------------------

    /**
     * Llamado por ChatBot (DEBE usar Platform.runLater) para añadir un usuario recibido por 352.
     * @param user El objeto IRCUser detallado.
     */
    public void receiveUser(IRCUser user) {
        // El ChatBot debe asegurar que esta llamada está en Platform.runLater.
        // Si no estás seguro, envuélvelo en Platform.runLater aquí también.
        
        // Manteniendo el código que te causaba el error (ahora debe funcionar si ChatBot lo llama en FX Thread)
        users.add(user);
        
        Window window = userTable.getScene().getWindow();
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            stage.setTitle("Usuarios en " + currentChannel + " (" + users.size() + " cargados)");
        }
    }

    /**
     * Llamado por ChatBot (DEBE usar Platform.runLater) al recibir el código 315 (Fin de WHO).
     */
    public void finishQuery(String channelName) {
        // El ChatBot debe asegurar que esta llamada está en Platform.runLater.

        if (!users.isEmpty()) {
            int totalUsers = users.size();
            lblChannelInfo.setText("Usuarios en el canal " + currentChannel + ": " + totalUsers + " encontrados.");
            
            Window window = userTable.getScene().getWindow();
            if (window instanceof Stage) {
                Stage stage = (Stage) window;
                stage.setTitle("Usuarios en " + currentChannel + " (" + totalUsers + ")");
            }
        } else {
            lblChannelInfo.setText("Usuarios en el canal " + currentChannel + ": 0 encontrados.");
        }
    }
}