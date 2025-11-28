package java_irc_chat_client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.IOException; // Importación necesaria para el manejo de excepciones de FXML

import irc.ChatBot;

public class MainApp extends Application {

    // --- Configuración de Conexión IRC ---
    private static final String NICK = "El_ArWeN"; // Tu nick predeterminado
    private static final String LOGIN = "El_ArWeN"; // Tu login predeterminado
    private static final String SERVER = "lima.chatzona.org"; // Servidor IRC

    @Override
    public void start(Stage primaryStage) throws Exception {
        VBox rootVBox = new VBox();

        // --- Cargar Menú y ToolBar ---
        MenuBar menuBar = FXMLLoader.load(getClass().getResource("menu.fxml"));
        rootVBox.getChildren().add(menuBar);

        FXMLLoader toolbarLoader = new FXMLLoader(getClass().getResource("toolbar.fxml"));
        ToolBar toolBar = toolbarLoader.load();
        ToolBarController tbController = toolbarLoader.getController();

        // --- Crear SplitPane y Paneles ---
        SplitPane splitPane = new SplitPane();

        VBox leftPane = new VBox();
        leftPane.setPrefWidth(150);
        leftPane.setStyle("-fx-border-color: orange; -fx-border-width: 2;");
        leftPane.setSpacing(10);

        StackPane rightPane = new StackPane();

        // Asignación de Panes al ToolBarController (Mantenida la funcionalidad)
        tbController.setLeftPane(leftPane);
        tbController.setRightPane(rightPane);

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(150.0 / 1200);
        
        // Listener para mantener el ancho del panel izquierdo (Mantenida la funcionalidad)
        splitPane.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
            splitPane.getDividers().get(0).setPosition(150.0 / splitPane.getWidth());
        });

        VBox.setVgrow(splitPane, Priority.ALWAYS);
        rootVBox.getChildren().addAll(toolBar, splitPane);

        // --- Configuración de la Ventana Principal ---
        Scene scene = new Scene(rootVBox, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("JIRCHAT");
        
        // ======================================================================
        // ⭐ NUEVA FUNCIONALIDAD: INYECCIÓN DE DEPENDENCIA DEL CHATBOT ⭐
        // ======================================================================

        // 1. Abrir Chat y obtener la referencia al ChatController
        // Se asume que tbController.abrirChat() devuelve la instancia de ChatController.
        ChatController mainController = tbController.abrirChat(primaryStage);
        
        
     
        
        if (mainController != null) {
            
            // 2. Crear la instancia del ChatBot, pasándole el controlador principal
            ChatBot bot = new ChatBot(mainController, NICK, LOGIN); 

            // ⭐ 3. VINCULAR LA INSTANCIA (INYECCIÓN DE DEPENDENCIA) ⭐
            // Esto resuelve el problema del 'bot is null' en el ChatController.
            mainController.setBot(bot); 

            // 4. Iniciar la conexión al servidor IRC
            try {
                // Conectar al servidor principal
                bot.connect(SERVER); 
            } catch (IOException e) {
                // Manejar error de conexión
                System.err.println("❌ ERROR: No se pudo conectar al servidor IRC " + SERVER);
                e.printStackTrace();
            }
        } else {
             System.err.println("❌ ERROR: No se pudo obtener la instancia del ChatController.");
        }

        primaryStage.show();
        
        tbController.abrirVentanaConexionDesdeInicio();
    }

    public static void main(String[] args) {
        launch(args);
    }
}



