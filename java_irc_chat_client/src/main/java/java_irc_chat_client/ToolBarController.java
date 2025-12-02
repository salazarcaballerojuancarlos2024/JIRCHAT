package java_irc_chat_client;

import javafx.event.ActionEvent;


import java.io.IOException;

// ELIMINADO: import dcc.TransferManager; 
import irc.ChatBot; 
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Accordion; 
import javafx.scene.control.TitledPane; 



public class ToolBarController {

    private StackPane rightPane;
    private VBox leftPane;
    private ChatController chatController;
    private AnchorPane statusPane;
    private AnchorPane currentFrontPane;
    private VBox vboxCanales = new VBox(5);
    private VBox vboxPrivados = new VBox(5);
    private VBox vboxUsuarios = new VBox(5);
    // ⭐ El Accordion que contendrá los VBox
    private Accordion mainAccordion;
 // ⭐ NUEVOS CAMPOS para guardar las referencias de las pestañas
    private TitledPane tpCanales;
    private TitledPane tpPrivados;
   

    public void setRightPane(StackPane rightPane) { this.rightPane = rightPane; }
    public void setLeftPane(VBox leftPane) { this.leftPane = leftPane; }
    public ChatController getChatController() { return chatController; }
    
    private ListView<UsuarioConocido> knownUsersListView;
    
    

    @FXML
    private Button btnUserList;
    
    
    @FXML
    private Button btnConnect;

    // ELIMINADO: public void setTransferManager(TransferManager transferManager) { ... } // Ya no es necesario

    
    @FXML
    private void onSetupButtonClicked() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_SETUP.fxml"));
            Parent root = loader.load();

            SetupController setupController = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Configuración");
            stage.setScene(new Scene(root));
            stage.setResizable(false);

            setupController.setStage(stage);

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    
    @FXML
    private void abrirLogs() {
        try {
        	FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_LOGS.fxml"));

            AnchorPane root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Logs");
            stage.setScene(new Scene(root));
            stage.setResizable(false); 
            stage.initModality(Modality.NONE); 
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Error abriendo ventana de logs: " + e.getMessage(),
                    ButtonType.OK);
            alert.showAndWait();
        }
    }



 // Dentro de ToolBarController.java

    /**
     * Abre la ventana de usuarios conocidos, fuerza la recarga del XML 
     * e inyecta el ChatController para sincronizar los datos.
     */
    @FXML
    private void abrirUserList() {
        try {
            // Crear un FXMLLoader nuevo cada vez
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_LISTAS_USUARIOS_CONOCIDOS.fxml"));
            Parent root = loader.load();

            // Obtener controlador recién creado
            UsuariosController controller = loader.getController();

            // ⭐ INYECCIÓN CRÍTICA: Pasar la referencia del ChatController
            if (chatController == null) {
                // Si el ChatController es null (no hay conexión iniciada),
                // se puede seguir, pero la funcionalidad de sincronización fallará.
                System.err.println("Advertencia: ChatController no inicializado al abrir Lista de Usuarios.");
            } else {
                // Permitir que UsuariosController llame a métodos del ChatController 
                // (ej. addNewKnownUser)
                controller.setChatController(chatController);
            }

            // Cargar los datos desde el XML siempre al abrir la ventana
            controller.cargarUsuariosDesdeXML();

            Stage stage = new Stage();
            stage.setTitle("Usuarios Conocidos");
            stage.setScene(new Scene(root));
            // Establecer la ventana principal como dueña (si está disponible)
            stage.initOwner(btnUserList.getScene().getWindow());
            stage.initModality(Modality.NONE);

            // Limpiar tabla al cerrar la ventana para evitar cache de datos
            stage.setOnCloseRequest(event -> controller.limpiarTabla());

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Error al abrir la ventana de Usuarios Conocidos: " + e.getMessage(),
                    ButtonType.OK);
            alert.showAndWait();
        }
    }
    
    @FXML
    private void abrirVentanaConexion(ActionEvent event) {
        try {
            // Cargar FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_CONEXION.fxml"));
            Parent root = loader.load();

            // Obtener el controlador de la ventana de conexión
            ConexionController controller = loader.getController();
            
            // Cargar el chatController en el objeto ConexionController
            if(controller.comprobarChatController()) controller.setChatController(this.chatController);
            // Cargar los datos directamente desde el fichero físico
            controller.cargarFormulario(); 

            // Crear la nueva ventana
            Stage stage = new Stage();
            stage.setTitle("Conexión IRC");
            stage.setScene(new Scene(root));
            stage.setResizable(true);
            stage.initOwner(btnConnect.getScene().getWindow()); 
            stage.initModality(Modality.NONE);

            // Registrar el guardado al cerrar la ventana
            stage.setOnCloseRequest(e -> {
                try {
                    controller.guardarFormulario(); 
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "Error guardando formulario al cerrar: " + ex.getMessage(),
                            ButtonType.OK);
                    alert.showAndWait();
                }
            });

            // Mostrar la ventana
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Error abriendo la ventana de conexión: " + e.getMessage(),
                    ButtonType.OK);
            alert.showAndWait();
        }
    }

 

    public void abrirVentanaConexionDesdeInicio() {
        try {
            // Cargar FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_CONEXION.fxml"));
            Parent root = loader.load();

            // Obtener el controlador de la ventana de conexión
            ConexionController controller = loader.getController();

            // ⭐ INYECCIÓN CRÍTICA: Dar al controlador de conexión acceso al ChatController
            // Esto es NECESARIO para que el botón "Conectar" inicie la conexión real.
            if (chatController == null) {
                throw new IllegalStateException("El ChatController principal no ha sido inicializado.");
            }
            controller.setChatController(chatController); 

            // Cargar los datos guardados en el formulario
            controller.cargarFormulario(); 

            // Crear la nueva ventana (Stage)
            Stage stage = new Stage();
            stage.setTitle("Conexión IRC");
            stage.setScene(new Scene(root));
            stage.setResizable(false); 
            
            // Determinar la Stage principal para establecerla como propietaria
            // Esto mantiene la ventana de conexión sobre la ventana principal.
            Window primarySceneWindow = rightPane != null ? rightPane.getScene().getWindow() : null;
            Stage primaryStage = (primarySceneWindow instanceof Stage) ? (Stage) primarySceneWindow : null;
            
            if (primaryStage != null) {
                 stage.initOwner(primaryStage); 
            }

            // Bloquear la ventana principal hasta que se conecte o cierre
            stage.initModality(Modality.APPLICATION_MODAL); 

            // Registrar el guardado al cerrar la ventana (si el usuario cierra sin conectar)
            stage.setOnCloseRequest(e -> {
                try {
                    // Guardar la configuración actual (manual o auto-conector)
                    controller.guardarFormulario(); 
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "Error guardando formulario al cerrar: " + ex.getMessage(),
                            ButtonType.OK);
                    alert.showAndWait();
                }
            });

            // Mostrar la ventana
            stage.show();
            
            // Centrar la ventana de conexión en la pantalla
            if (primaryStage != null) {
                stage.centerOnScreen();
            }

        } catch (IOException e) {
            // Error al cargar el FXML (ruta incorrecta o problema de archivo)
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Error al cargar la interfaz de conexión (FXML): " + e.getMessage(),
                    ButtonType.OK);
            alert.showAndWait();
        } catch (IllegalStateException e) {
             // Error de inicialización del ChatController
             e.printStackTrace();
             Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Error de dependencias: " + e.getMessage(),
                    ButtonType.OK);
             alert.showAndWait();
        } catch (Exception e) {
            // Captura cualquier otra excepción general (ej. error al cargar formulario)
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Error abriendo la ventana de conexión: " + e.getMessage(),
                    ButtonType.OK);
            alert.showAndWait();
        }
    }

    
 @FXML
 public ChatController abrirChat(Stage primaryStage) {
     // Si ya está abierto, devolvemos la instancia existente.
     if (chatController != null) {
         return chatController; // ⭐ CAMBIO: Devolver la instancia existente
     }

     try {
         FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_CONNECT_STAGE.fxml"));
         AnchorPane chatPane = loader.load();
         
         // Mantenemos la asignación a la variable de instancia del ToolBarController
         chatController = loader.getController(); 

         // --- 1. CONSTRUCCIÓN DE LA ESTRUCTURA IZQUIERDA (Status + Accordion) ---
         
         // Crear el Accordion y sus TitledPanes, guardando las referencias internas
         mainAccordion = createChannelAccordion();
         
         // Crear el botón Status
         Button statusButton = new Button("Status");
         statusButton.setMaxWidth(Double.MAX_VALUE);
         statusButton.setOnAction(e -> showStatus());
         
         // Limpiar el leftPane (el VBox contenedor principal) y añadir los elementos
         if (leftPane != null) {
             leftPane.getChildren().clear(); // Limpiamos cualquier contenido anterior
             leftPane.getChildren().add(statusButton); // Añadir el botón Status (fijo arriba)
             leftPane.getChildren().add(mainAccordion);  // Añadir el Accordion (debajo)
         }
         
         // --- 2. INYECCIÓN CRÍTICA DE REFERENCIAS AL CHATCONTROLLER ---
         
         // Inyectar el panel derecho principal (Mantenido)
         chatController.setRightPane(rightPane);
         
         // Inyectar los VBox internos (Mantenido)
         chatController.setVboxCanales(vboxCanales);
         chatController.setVboxPrivados(vboxPrivados);
         chatController.setVboxUsuarios(vboxUsuarios);
         
         // Inyectar la estructura del Accordion para la funcionalidad de expansión (Mantenido)
         chatController.setMainAccordion(mainAccordion);
         chatController.setTpCanales(tpCanales);
         chatController.setTpPrivados(tpPrivados);
         
      // ⭐ LLAMADA CRÍTICA: CARGAR LOS USUARIOS AL LISTVIEW (Mantenido)
         chatController.loadKnownUsersFromXML();
         
         // --- 3. CONFIGURACIÓN FINAL Y CONEXIÓN ---

         // Añadir el panel de chat (Status) al rightPane (Mantenido)
         rightPane.getChildren().add(chatPane);

         // Guardar statusPane y ponerlo en primer plano (Mantenido)
         statusPane = (AnchorPane) chatController.getRootPane();
         currentFrontPane = statusPane;

         // Abrir la ventana de Conexión (donde se creará el bot y se iniciará la conexión) (Mantenido)
         //abrirVentanaConexionDesdeInicio();

         // Vincular ventanas flotantes al primaryStage (Mantenido)
         if (primaryStage != null) {
             chatController.bindFloatingWindowsToRightPane(primaryStage);
         }
         
         // ⭐ CAMBIO: Devolver el controlador recién creado y configurado
         return chatController; 

     } catch (IOException e) {
         e.printStackTrace();
         // ⭐ CAMBIO: Devolver null si la carga falla
         return null; 
     }
 }


 /**
  * Método auxiliar que crea el Accordion con los 3 TitledPanes y ScrollPanes.
  */
 private Accordion createChannelAccordion() {
	    Accordion accordion = new Accordion();
	    
	    // --- Pestaña 1: CANALES ---
	    ScrollPane scrollCanales = new ScrollPane(vboxCanales);
	    scrollCanales.setFitToWidth(true); 
	    tpCanales = new TitledPane("Canales", scrollCanales); // ⭐ REFERENCIA GUARDADA
	    
	    // --- Pestaña 2: PRIVADOS ---
	    ScrollPane scrollPrivados = new ScrollPane(vboxPrivados);
	    scrollPrivados.setFitToWidth(true);
	    tpPrivados = new TitledPane("Privados", scrollPrivados); // ⭐ REFERENCIA GUARDADA
	    
	 // --- Pestaña 3: USUARIOS ---
	 // ⭐ CREAR EL LISTVIEW DE USUARIOS
	 knownUsersListView = new ListView<>();
	 // Configurar la factory de celdas para usar nuestra clase personalizada
	 knownUsersListView.setCellFactory(param -> new UsuarioConocidoCell());
	 knownUsersListView.setItems(chatController.getKnownUsersList()); // Asumimos que hay un getter en ChatController

	 ScrollPane scrollUsuarios = new ScrollPane(knownUsersListView); // Usar el ListView aquí
	 scrollUsuarios.setFitToWidth(true);
	 TitledPane tpUsuarios = new TitledPane("Usuarios", scrollUsuarios);
	    
	    // Añadir las pestañas al Accordion
	    accordion.getPanes().addAll(tpCanales, tpPrivados, tpUsuarios);
	    
	    // Expandir la primera pestaña por defecto (opcional)
	    accordion.setExpandedPane(tpCanales); 
	    
	    return accordion;
	}


    /**
     * Muestra el panel de estado en primer plano.
     */
    private void showStatus() {
        if (statusPane == null) return;

        if (currentFrontPane == null || currentFrontPane == statusPane) {
            statusPane.toFront();
            currentFrontPane = statusPane;
        }
    }
    

    @FXML
    private void abrirVentanaListadodeCanales() {
        // 1. Verificar la conexión inmediatamente
        ChatBot bot = chatController.getBot();
        if (bot == null || !bot.isConnected()) {
            chatController.appendSystemMessage("⚠ No hay conexión IRC activa o no está completa. No se puede listar canales.");
            return; 
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("JIRCHAT_LISTA_DE_CANALES.fxml"));
            Parent root = loader.load();

            CanalesListController controller = loader.getController();
            
            // ⭐ ACCIÓN CLAVE: Unificar la inicialización y la carga.
            // Llamamos al método setBot, que ahora es responsable de:
            // 1. Almacenar el bot y el chatController.
            // 2. Inicializar las listas filtradas/ordenadas.
            // 3. Llamar a bot.getCanales(handler) para iniciar la solicitud LIST.
            controller.setBot(bot, chatController); // Pasamos bot y chatController
            
            // 4. Mostrar la ventana (la carga de datos ocurrirá de fondo y poblará la tabla)
            Stage stage = new Stage();
            stage.setTitle("Listado de Canales");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            if (chatController != null) {
                chatController.appendSystemMessage("⚠ Error al abrir ventana de listado de canales: " + e.getMessage());
            } else {
                System.err.println("Error: chatController es nulo al intentar abrir lista de canales.");
            }
        }
    }
}