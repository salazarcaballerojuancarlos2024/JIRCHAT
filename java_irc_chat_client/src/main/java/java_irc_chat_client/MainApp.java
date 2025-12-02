package java_irc_chat_client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.IOException;

// Importación ChatBot eliminada, ya que no se usa directamente en start()
// import irc.ChatBot; 

public class MainApp extends Application {

    // --- Configuración de Conexión IRC ---
    // ELIMINADAS: NICK, LOGIN, SERVER. Estos serán gestionados por ConexionController.

    // ⭐ Referencia al controlador principal (Necesario para inyectar en el formulario)
    private ChatController mainChatController;

    @Override
    public void start(Stage primaryStage) throws Exception {
        
        // --- 1. Cargar la Estructura Principal de la UI ---
        VBox rootVBox = new VBox();
        MenuBar menuBar = FXMLLoader.load(getClass().getResource("menu.fxml"));
        rootVBox.getChildren().add(menuBar);

        FXMLLoader toolbarLoader = new FXMLLoader(getClass().getResource("toolbar.fxml"));
        ToolBar toolBar = toolbarLoader.load();
        ToolBarController tbController = toolbarLoader.getController();

        SplitPane splitPane = new SplitPane();
        VBox leftPane = new VBox();
        leftPane.setPrefWidth(150);
        leftPane.setStyle("-fx-border-color: orange; -fx-border-width: 2;");
        leftPane.setSpacing(10);
        StackPane rightPane = new StackPane();

        tbController.setLeftPane(leftPane);
        tbController.setRightPane(rightPane);

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(150.0 / 1200);
        
        splitPane.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
            splitPane.getDividers().get(0).setPosition(150.0 / splitPane.getWidth());
        });

        VBox.setVgrow(splitPane, Priority.ALWAYS);
        rootVBox.getChildren().addAll(toolBar, splitPane);

        Scene scene = new Scene(rootVBox, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("JIRCHAT");
        
        // ======================================================================
        // ⭐ CONFIGURACIÓN DEL CHAT CONTROLLER ⭐
        // ======================================================================

        // 2. Abrir Chat y obtener la referencia al ChatController
        // Esto carga la interfaz de chat (pestañas, etc.) en el rightPane
        mainChatController = tbController.abrirChat(primaryStage);
        
        if (mainChatController == null) {
             System.err.println("❌ ERROR: No se pudo obtener la instancia del ChatController. La aplicación no continuará.");
             return;
        }
        
        // ⭐ IMPORTANTE: Ahora, el ChatController NO tiene un Bot o conexión inicial.
        // El ChatController tiene la responsabilidad de crear el ChatBot y conectar
        // cuando reciba los parámetros (servidor/nick) del formulario de conexión.

        // 3. Mostrar la ventana principal
        primaryStage.show();
        
        // 4. Abrir la ventana de conexión (FormularioConexion.fxml)
        // El tbController debe abrir la ventana e inyectarle el mainChatController
        tbController.abrirVentanaConexionDesdeInicio(mainChatController);
    }
    
    // NOTA: Debes asegurarte de que el método abrirVentanaConexionDesdeInicio 
    // en ToolBarController ahora acepta un ChatController y lo inyecta en el ConexionController.

    public static void main(String[] args) {
        launch(args);
    }
}



