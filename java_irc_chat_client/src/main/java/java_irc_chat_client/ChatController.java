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
import javafx.scene.control.Accordion;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox; // Si no lo tienes ya

import org.w3c.dom.Element; // Import necesario
import org.w3c.dom.NodeList; // Import necesario
import javax.xml.parsers.DocumentBuilder; // Import necesario
import javax.xml.parsers.DocumentBuilderFactory; // Import necesario
import org.w3c.dom.Document; // Import necesario
import java.util.ArrayList;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class ChatController {

    @FXML private TextField inputField;
    @FXML private ListView<String> userListView_canal;
    @FXML private ScrollPane chatScroll;
    @FXML private TextArea  chatFlow; // Parece no usarse, usamos chatArea
    @FXML private TextArea chatArea; // Consola principal
    @FXML private Accordion mainAccordion;
    @FXML private VBox vboxCanales;
    @FXML private VBox vboxPrivados;
    @FXML private VBox vboxUsuarios; // Donde irán los iconos de usuarios
    @FXML private ListView<UsuarioConocido> listaUsuariosConocidos;
    
    @FXML private TableView<UsuarioConocido> userTableView; // O <IRCUser>
    @FXML private TableColumn<UsuarioConocido, Boolean> statusColumn;
    
    private TitledPane tpCanales;
    private TitledPane tpPrivados;
    @FXML private TitledPane tpUsuarios; 
    
 
    private String channelName;
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
    
     // ⭐ NUEVOS CAMPOS PARA EL LISTVIEW DE USUARIOS CONOCIDOS
     // Si lo inyectas desde FXML
    @FXML private ListView<UsuarioConocido> knownUsersListView; 
        
    // La lista observable que contiene los datos del ListView
    private final ObservableList<UsuarioConocido> knownUsers = FXCollections.observableArrayList();
    private static final String FILE_PATH_USUARIOS = System.getProperty("user.home") + "/.jirchat/usuarios_conocidos.xml";
    
    
    
    /**
     * Elimina un usuario de la lista de usuarios conocidos que alimenta el ListView
     * en el Accordion lateral.
     * @param nick El nick del usuario a eliminar.
     */
    public void removeKnownUser(String nick) {
        // La eliminación debe ocurrir en el hilo de JavaFX UI.
        Platform.runLater(() -> {
            // Usamos removeIf para eliminar el primer elemento que coincida con el nick
            knownUsers.removeIf(u -> u.getNick().equalsIgnoreCase(nick));
        });
    }
    
    
    /**
     * Lee los nicks del XML y los carga en el ObservableList del ListView.
     */
    public void loadKnownUsersFromXML() {
        Platform.runLater(() -> {
            knownUsers.clear();
            try {
                File file = new File(FILE_PATH_USUARIOS);
                if (!file.exists()) return;

                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(file);
                doc.getDocumentElement().normalize();

                NodeList nList = doc.getElementsByTagName("usuario");
                List<UsuarioConocido> loaded = new ArrayList<>();

                for (int i = 0; i < nList.getLength(); i++) {
                    Element e = (Element) nList.item(i);
                    String nick = e.getAttribute("nick");
                    UsuarioConocido user = new UsuarioConocido(nick);
                    // ❗ Inicialmente, comprobamos el estado de conexión
                    
                    loaded.add(user);
                }

                knownUsers.setAll(loaded);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    /**
     * 🔄 Actualiza el estado de conexión (conectado/desconectado) de un UsuarioConocido.
     * Es llamado por el ChatBot (onJoin, onPart, etc.) para reflejar la presencia 
     * en la lista global, utilizando la "fuente de verdad" del bot.
     * * @param nick El nickname del usuario.
     * @param isConnectedByEvent El estado de conexión sugerido por el evento (no usado para la confirmación).
     */
    public void updateConnectionStatus(String nick, boolean isConnectedByEvent) {
        
        // ⭐ 1. CRÍTICO: Aseguramos la ejecución en el hilo de JavaFX para manipular la UI.
        Platform.runLater(() -> {
            
            if (bot == null) return; 

            // 2. FUENTE DE VERDAD: Confirmar el estado de conexión con la lista interna del bot.
            boolean confirmedStatus = bot.isNickConnected(nick);

            // 3. Buscar el usuario en la lista observable de la UI.
            for (UsuarioConocido user : this.knownUsers) {
                
                // Comparamos el nick ignorando mayúsculas/minúsculas.
                if (user.getNick().equalsIgnoreCase(nick)) { 
                    
                    // 4. Solo actualizar si el estado ACTUAL de la UI es diferente al estado CONFIRMADO.
                    if (user.isConectado() != confirmedStatus) {
                        
                        // ⭐ ACCIÓN CLAVE: Establecer la propiedad del objeto UsuarioConocido.
                        user.setConectado(confirmedStatus); 
                        
                        // 5. ⭐ CORRECCIÓN CRÍTICA: Forzar el refresco de la TableView.
                        // Esto asegura que el CellFactory (que aplica el fondo verde) se ejecute 
                        // inmediatamente después de cambiar el estado.
                        if (userTableView != null) {
                            userTableView.refresh();
                        }
                        
                        // log.debug("🟢 Estado de conexión actualizado para {}: {}", nick, confirmedStatus ? "Conectado" : "Desconectado");
                    }
                    
                    return; // Detenemos la búsqueda (el usuario conocido fue encontrado y procesado).
                }
            }
            // log.debug("Usuario {} no encontrado en la lista de conocidos. No se actualizó el estado visual.", nick);
        });
    }
    
 
    
    
    public ObservableList<UsuarioConocido> getKnownUsersList() {
        return knownUsers;
    }
    
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
    
 // Dentro de ChatController.java

 // --- INICIALIZACIÓN ---
     
    @FXML
    public void initialize() {
        
        // --- 1. Configuración del Campo de Entrada (inputField) ---
        if (inputField != null) {
            // Deshabilitar el campo de texto. Se habilitará en syncFinished()
            inputField.setDisable(true); 
            
            // Acción a ejecutar al presionar ENTER
            inputField.setOnAction(e -> sendCommand());
            
            // Consumir el evento TAB para que no cambie el foco (opcional)
            inputField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.TAB) {
                    event.consume();
                }
            });
        }

        // --- 2. Configuración de la Pausa de Redimensionamiento (resizePause) ---
        if (resizePause != null) {
            resizePause.setOnFinished(ev -> {
                if (lastFocusedWindow != null) {
                    Platform.runLater(() -> lastFocusedWindow.toFront());
                }
            });
        }
        
        // --- 3. Configuración de la Lista de Usuarios Conocidos Globalmente ---
        if (knownUsersListView != null) { 
            // 3.1 VINCULAR la lista Observable a la vista (ListView)
            knownUsersListView.setItems(knownUsers); 
            
            // 3.2 Configurar la fábrica de celdas para el sombreado verde
            setupKnownUsersListCellFactory(); 
        }
    }

    
  
 // Dentro de ChatController.java (Método regenerado)

    /**
     * Configura el factory de celdas para la lista de usuarios conocidos (knownUsersListView).
     * Esto es CRÍTICO para aplicar estilos basados en la propiedad 'conectado'
     * de la clase UsuarioConocido, haciéndolo reactivo a los cambios de estado.
     */
    private void setupKnownUsersListCellFactory() { 
        
        // 1. Verificación de seguridad: Asegurar que el ListView exista.
        if (knownUsersListView == null) {
            System.err.println("ERROR: knownUsersListView no está inicializado.");
            return; 
        }
        
        // Usamos knownUsersListView para establecer la fábrica de celdas
        knownUsersListView.setCellFactory(lv -> new ListCell<UsuarioConocido>() {
            
            // ⭐ LISTENER CLAVE: Se usa un listener para reaccionar al cambio de la propiedad 'conectado'
            private javafx.beans.value.ChangeListener<Boolean> conectadoListener;
            
            @Override
            protected void updateItem(UsuarioConocido usuario, boolean empty) { 
                super.updateItem(usuario, empty);

                // 1. Limpiar listener anterior si existe
                if (conectadoListener != null && getItem() != null) {
                    // Desvincular el listener del viejo objeto UsuarioConocido
                    getItem().conectadoProperty().removeListener(conectadoListener);
                }
                
                if (empty || usuario == null) {
                    setText(null);
                    setStyle(null); // Limpiar estilos
                    conectadoListener = null; // Limpiar referencia al listener
                    
                } else {
                    // 2. Mostrar el nick
                    setText(usuario.getNick());

                    // 3. Crear el listener si no existe
                    if (conectadoListener == null) {
                        conectadoListener = (obs, oldVal, newVal) -> {
                            // Cuando la propiedad 'conectado' cambia, forzamos un redibujado de esta celda
                            // Esto garantiza que el estilo se actualice inmediatamente.
                            updateStyle(usuario);
                        };
                    }
                    
                    // 4. Vincular el listener a la propiedad del objeto actual
                    usuario.conectadoProperty().addListener(conectadoListener);

                    // 5. Aplicar el estilo inicial
                    updateStyle(usuario);
                }
            }
            
            /**
             * Aplica el estilo basado en el estado 'conectado' del UsuarioConocido.
             * @param usuario El objeto UsuarioConocido.
             */
            private void updateStyle(UsuarioConocido usuario) {
                // ⭐ LÓGICA DE RESALTADO REACTIVO ⭐
                if (usuario.isConectado()) {
                    // Verde fosforescente para el texto y negrita
                    // Nota: Si quieres fondo verde fosforescente (#39FF14), usa -fx-background-color
                    setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-background-color: #39FF14;");
                } else {
                    // Negro/Defecto
                    setStyle("-fx-text-fill: black; -fx-font-weight: normal; -fx-background-color: transparent;");
                }
            }
        });
    }


/**
* Configura la TableView para los usuarios conocidos, vinculando las columnas
* al modelo UsuarioConocido, y establece el CellFactory para la columna de estado
* que cambia el color de la celda.
*/
//Dentro de ChatController.java

private void setupKnownUsersList() {
  // ... (Pasos 1 y 2 se mantienen igual) ...
  // 1. Vincular la TableView con el modelo de datos observable (knownUsers)
  userTableView.setItems(knownUsers);

  // 2. Establecer la fábrica de valores para la columna de estado.
  statusColumn.setCellValueFactory(cellData -> {
      UsuarioConocido user = cellData.getValue();
      return user.conectadoProperty(); 
  });

  // 3. ⭐ APLICAR EL CELL FACTORY MODIFICADO ⭐
  statusColumn.setCellFactory(column -> new TableCell<UsuarioConocido, Boolean>() {
      @Override
      protected void updateItem(Boolean item, boolean empty) {
          super.updateItem(item, empty);
          
          // Limpiar la celda si está vacía
          if (empty || item == null) {
              setText(null);
              setStyle(""); // Sin estilo
          } else {
              // El 'item' es el valor booleano (True/False)
              if (item) {
                  // ✅ ESTADO CONECTADO: Fondo Verde
                  setStyle("-fx-background-color: #a8ffa8; -fx-alignment: CENTER;"); 
                  setText("ON"); 
              } else {
                  // ❌ ESTADO DESCONECTADO/NEUTRO: Quitamos cualquier estilo
                  setStyle(""); // ¡AQUÍ ESTÁ LA MODIFICACIÓN CLAVE!
                  setText("OFF"); // El texto sí se queda como "OFF"
              }
          }
      }
  });
}

//Dentro de ChatController.java (Añadir a la sección de auxiliares)

/**
* Delega la actualización del número de usuarios total al CanalController específico.
* Es llamado por ChatBot.onUserList para actualizar el Label del canal.
* @param channelName El nombre del canal (ej: #general).
* @param count El número total de usuarios.
*/
public void actualizarContadorUsuarios(String channelName, int count) {
 final String key = channelName.toLowerCase(); 
 
 // Se asume que 'CanalVentana' es la clase wrapper con el Stage y el Controller.
 CanalVentana wrapper = canalesAbiertos.get(key);
 
 if (wrapper != null && wrapper.controller != null) {
     // Llama al método del CanalController
     Platform.runLater(() -> {
         wrapper.controller.updateUserCount(count); 
     });
 }
}

    // --- LÓGICA DE BOTONES Y VENTANAS (CANALES) ---

//Dentro de ChatController.java

/**
* Crea, configura y abre una nueva ventana (Stage) para el canal especificado.
* Incluye la inyección de la lista de nicks conocidos para el resaltado verde.
*
* @param channelName El nombre del canal (ej: #java_irc).
* @throws IOException Si falla la carga del FXML.
*/
public void agregarCanalAbierto(String channelName) throws IOException {
 final String key = channelName.toLowerCase();
 
 // 1. Evitar duplicados y comprobar si ya está abierta
 if (!canalesUnidos.contains(channelName)) {
     canalesUnidos.add(channelName);
 }
 if (canalesAbiertos.containsKey(key)) {
     CanalVentana existente = canalesAbiertos.get(key);
     if (existente != null && existente.stage != null) {
         // Si ya está abierta, la trae al frente y termina la ejecución
         existente.stage.toFront();
     }
     return;
 }

 // 2. Crear y cargar la nueva ventana (Stage) del canal
 try {
     FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_CANAL.fxml"));
     Parent root = loader.load();
     CanalController canalController = loader.getController();

     // 3. Configurar el controlador y la lógica de negocio
     canalController.setCanal(channelName);
     canalController.setBot(this.bot); 
     canalController.setMainController(this);
     
     // ⭐ PASO CRÍTICO: INYECCIÓN PARA EL RESALTADO VERDE ⭐
     // 3.1. Obtener el Set de nicks conocidos locales (transformados a minúsculas)
     Set<String> nicksConocidos = getLocalKnownNicksSet(); 
     
     // 3.2. Inyectar el Set en el controlador del canal
     // (El CanalController usa este Set en su CellFactory)
     canalController.setKnownNicksSet(nicksConocidos); 
     // -------------------------------------------------------------------

     if (this.bot != null) {
         // Registrar el controlador como delegado para manejar las respuestas de NAMES
         this.bot.registerNamesDelegate(channelName, canalController); 
     }
     
     // 4. Crear la ventana (Stage)
     Stage stage = new Stage();
     stage.setTitle(channelName + " - " + bot.getNick()); // Usar getNick() del bot
     stage.setScene(new Scene(root));

     // 5. Almacenar la ventana y el controlador en el mapa
     CanalVentana nuevoWrapper = new CanalVentana(stage, canalController);
     canalesAbiertos.put(key, nuevoWrapper);
     
     // 6. INTEGRACIÓN DEL BOTÓN Y MANEJO DE CIERRE
     agregarBotonCanalPrivadoDcc(channelName, stage, "canal");
     
     // Configuración del evento de cierre (al pulsar la 'X')
     stage.setOnCloseRequest(event -> {
         if (this.bot != null) {
             this.bot.partChannel(channelName);
             appendSystemMessage("👋 Has abandonado el canal " + channelName); 
             // Desregistrar el delegado al cerrar
             this.bot.registerNamesDelegate(channelName, null); 
         }
         // Limpiar el botón y las referencias en la UI
         removerBotonCanal(channelName);
         cerrarCanalDesdeVentana(channelName); 
     });
     
     // 7. Mostrar la ventana y lógica condicional JOIN/NAMES
     stage.show();

     if (this.bot != null) {
         // Si ya estamos unidos (ej: reconexión), solo pedimos la lista de usuarios.
         if (this.bot.isJoined(channelName)) { 
             this.bot.sendRawLine("NAMES " + channelName); 
         } else {
             // Si no estamos unidos (ej: /join manual), nos unimos.
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
    
    

    public void cerrarCanalDesdeVentana(String canal) {
        // La lógica de cierre en el stage.setOnCloseRequest ya maneja el PART y la remoción del botón.
        CanalVentana ventana = canalesAbiertos.remove(canal.toLowerCase());
        if (ventana != null) Platform.runLater(() -> ventana.stage.close());
        floatingWindows.remove(ventana.stage);
    }
    
    // --- LÓGICA DE BOTONES Y VENTANAS (PRIVADOS) ---

 // Dentro de ChatController.java

    /**
     * Abre una nueva ventana de chat privado para el nick especificado.
     * Este método ahora incluye la lógica para cargar el historial del log.
     * @param nick El nick del usuario con el que se va a chatear.
     */
 // Dentro de ChatController.java

    public void abrirPrivado(String nick) {
        // Usamos el nick como clave de log y como título de ventana
        final String logName = nick; 

        try {
            // 1. Cargar el FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/java_irc_chat_client/JIRCHAT_PRIVADO.fxml"));
            Parent root = loader.load();
            PrivadoController privadoController = loader.getController();

            // 2. Configurar el controlador
            privadoController.setDestinatario(nick);
            privadoController.setBot(this.bot);
            privadoController.setMainController(this);

            // 3. ⭐ INTEGRACIÓN DEL LOGGING: Cargar el historial
            // Se asume que ChatLogger.cargarHistorial(nick) devuelve List<String>
            List<String> historial = ChatLogger.cargarHistorial(logName);
            
            // Usar el método SIN LOGGING (appendRawLogLine) para mostrar cada línea del historial
            for (String linea : historial) {
                privadoController.appendRawLogLine(linea); 
            }

            // 4. Crear la ventana (Stage)
            Stage stage = new Stage();
            stage.setTitle("Chat Privado con " + nick);
            stage.setScene(new Scene(root));
            
            // ⭐ CONFIGURACIÓN CONSOLIDADA DEL CIERRE (SOLO UNA VEZ)
            // Esto garantiza que la limpieza se ejecute cuando se pulsa la 'X'.
            stage.setOnCloseRequest(event -> {
                // Cuando la ventana se cierra, el evento 'stage.close()' se ejecuta automáticamente
                // DESPUÉS de este handler, por lo que solo necesitamos la limpieza de referencias.
                // Usamos 'cerrarPrivado' para eliminar el botón y limpiar todo el estado.
                cerrarPrivado(nick); 
                
                // Nota: Aquí NO hacemos stage.close(), ya que la 'X' de la ventana lo hará por sí misma.
            });
            
            // 5. Almacenar la referencia y mostrar
            // Asumiendo que 'privateChats' es un Map<String, Stage> en ChatController
            privateChats.put(nick, stage); 

            // ⭐ INTEGRACIÓN DEL BOTÓN EN EL PANEL IZQUIERDO
            // Se asume que este método crea y añade el botón al panel lateral.
            agregarBotonCanalPrivadoDcc(nick, stage, "privado");

            // 6. Mostrar la ventana
            stage.show();
            
            // Llamar a initializeChat (o autoScroll) para asegurar el scroll al final del historial cargado
            privadoController.initializeChat(); 

        } catch (IOException e) {
            System.err.println("Error al intentar abrir la ventana de chat privado con " + nick + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


 // Dentro de ChatController.java

    public void cerrarPrivado(String nick) {
        final String key = nick.toLowerCase();
        
        // Cerrar ventana y limpiar referencias
        Stage stage = privateChats.remove(key);
        if (stage != null) stage.close();

        privateChatsController.remove(key);

        // ⭐ REMOCIÓN DEL BOTÓN PRIVADO: Ahora usa vboxPrivados
        Button btn = privadoButtons.remove(key);
        if (btn != null && vboxPrivados != null) { // <-- Se comprueba contra vboxPrivados
            Platform.runLater(() -> vboxPrivados.getChildren().remove(btn));
        }

        solicitudPendiente.remove(key);
    }

    // Nota: El método 'removerBotonCanal' también debe modificarse para usar 'vboxCanales'.
    public void removerBotonCanal(String channelName) {
        final String key = channelName.toLowerCase(); 
        Button btn = canalButtons.remove(key); 
        
        // ⭐ REMOCIÓN DEL BOTÓN CANAL: Ahora usa vboxCanales
        if (btn != null && vboxCanales != null) { 
            Platform.runLater(() -> vboxCanales.getChildren().remove(btn));
        }
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
    
 // Dentro de ChatController.java

    /**
     * Crea, configura y añade un botón para un canal, chat privado o transferencia DCC 
     * al panel izquierdo (leftPane).
     * * @param name El nombre del canal, nick del usuario, o identificador DCC.
     * @param stage El Stage (ventana) asociado que debe activarse.
     * @param tipo El tipo de elemento ("canal", "privado", "dcc").
     */
 // Dentro de ChatController.java

    /**
     * Crea, configura y añade un botón para un canal, chat privado o transferencia DCC 
     * al VBox interno correspondiente dentro del Accordion.
     */
 // Dentro de ChatController.java

 // Dentro de ChatController.java

    private void agregarBotonCanalPrivadoDcc(String name, Stage stage, String tipo) {
        final String key = name.toLowerCase();
        
        // 1. DETERMINAR Y ASIGNAR LOS CONTENEDORES Y MAPAS (Asignación Única)
        final Map<String, Button> targetMap;
        final VBox targetVBox;
        // ⭐ VARIABLE CLAVE: La cortinilla (TitledPane) a expandir
        final TitledPane paneToExpand; 
        
        switch (tipo) {
            case "canal":
                targetMap = canalButtons;
                targetVBox = vboxCanales;
                paneToExpand = tpCanales; // Se abre la pestaña Canales
                break;
            case "privado":
                targetMap = privadoButtons;
                targetVBox = vboxPrivados;
                paneToExpand = tpPrivados; // Se abre la pestaña Privados
                break;
            case "dcc":
                targetMap = dccButtons;
                targetVBox = vboxPrivados;
                paneToExpand = tpPrivados; // Se abre la pestaña Privados (para DCC)
                break;
            default:
                return; 
        }
        
        String text = switch (tipo) {
            case "canal" -> name;
            case "privado" -> "@" + name;
            case "dcc" -> "📥 DCC: " + name;
            default -> name;
        };

        // 2. Lógica de re-creación/remoción de botones existentes
        Button existingBtn = targetMap.get(key);
        if (existingBtn != null && targetVBox != null) {
            // La remoción se hace en el hilo de UI
            Platform.runLater(() -> targetVBox.getChildren().remove(existingBtn));
        }
        
        // 3. Crear el nuevo botón
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        
        // 4. Asignar acción (toFront)
        btn.setOnAction(e -> {
            if (stage != null && stage.isShowing()) {
                stage.toFront();
            } else {
                // Lógica de limpieza al pulsar un botón con ventana cerrada
                if (tipo.equals("privado")) cerrarPrivado(name);
                else if (tipo.equals("dcc")) removerBotonDcc(key);
                appendSystemMessage("⚠ La ventana de " + name + " ya estaba cerrada.");
            }
        });
        
        btn.getStyleClass().add(tipo + "-button"); 

        // 5. Almacenar la nueva referencia
        targetMap.put(key, btn);

        // ⭐ 6. AÑADIR EL BOTÓN AL VBOX INTERNO Y EXPANDIR EL ACCORDION
        if (targetVBox != null) {
            Platform.runLater(() -> {
                // Asegura que el botón se añade al contenedor
                targetVBox.getChildren().add(btn);
                
                // ⭐ LÓGICA DE EXPANSIÓN DE LA CORTINILLA
                if (mainAccordion != null && paneToExpand != null) {
                    // setExpandedPane() colapsa automáticamente el panel anterior
                    // y expande el nuevo panel.
                    mainAccordion.setExpandedPane(paneToExpand); 
                }
            });
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
 // Dentro de ChatController.java

    public void abrirChatPrivado(String nick) {
        final String key = nick.toLowerCase();
        
        if (privateChats.containsKey(key)) {
            Stage stage = privateChats.get(key);
            
            if (stage.isShowing()) {
                // Caso 1: La ventana existe y está visible.
                stage.toFront();
                return;
            } else {
                // Caso 2: La ventana existe en el mapa pero está cerrada (fallo en la limpieza anterior).
                // La limpiamos y procedemos a recrear.
                cerrarPrivado(nick); 
                // NOTA: Esto limpia el stage y el botón, haciendo que la próxima línea cree todo de nuevo.
            }
        }
        
        // Si no existe o acabamos de limpiarla, abrimos la ventana (y recreamos el botón).
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

 // Dentro de ChatController.java

    /**
     * 📥 Maneja la recepción de un mensaje privado del servidor.
     * Registra el mensaje en el log ANTES de mostrarlo en la UI.
     */
    public void onPrivateMessageRemoto(String nick, String mensaje) {
        
        // ⭐ 1. LOGGING: Registrar el mensaje entrante. 
        // Usamos el nick del remitente como el nombre del fichero log.
        // El usuario es el remitente (nick).
        ChatLogger.log(nick, nick, mensaje); 
        
        // ------------------------------------------------------------------
        
        // 2. Lógica de UI (Mostrar la ventana o notificar)
        if (privateChats.containsKey(nick.toLowerCase())) {
            // La ventana ya está abierta: la traemos al frente y delegamos la visualización.
            privateChats.get(nick.toLowerCase()).toFront();
            
            // 3. Delegar la visualización: 
            // ¡IMPORTANTE! appendPrivateMessage DEBE ASEGURARSE DE OBTENER EL CONTROLADOR
            // Y LLAMAR AL MÉTODO DEL CONTROLADOR (ej. privadoController.appendMessage),
            // Y ese método appendMessage AHORA DEBE ESTAR LIBRE DE LÓGICA DE LOGGING.
            appendPrivateMessage(nick, mensaje, false); 
            
        } else {
            // La ventana no está abierta: notificamos al usuario para que la abra.
            mostrarSolicitudPrivado(nick, mensaje);
        }
    }
    
 

    /**
     * 🔄 Maneja el cambio de nick de un usuario en la lista de Usuarios Conocidos.
     * Esto asegura que el sombreado verde se mantenga si el nick es renombrado.
     * @param oldNick El nick antiguo.
     * @param newNick El nick nuevo.
     */
    public void handleNickChange(String oldNick, String newNick) {
        Platform.runLater(() -> {
            // 1. Buscar el objeto UsuarioConocido con el nick antiguo
            UsuarioConocido targetUser = null;
            for (UsuarioConocido user : knownUsers) {
                if (user.getNick().equalsIgnoreCase(oldNick)) {
                    targetUser = user;
                    break;
                }
            }
            
            // 2. Si se encuentra, actualiza su propiedad de Nick
            if (targetUser != null) {
                targetUser.nickProperty().set(newNick);
                log.info("Nick de usuario conocido actualizado de {} a {}.", oldNick, newNick);
                
                // ⭐ TRUCO DE JAVA FX: Para que la TableView refresque la celda, a veces es necesario 
                // forzar una pequeña actualización en la lista observable.
                // Esto es opcional, pero previene problemas de caché visual.
                // knownUsers.remove(targetUser);
                // knownUsers.add(targetUser); 
                // Un método más limpio es:
                userTableView.refresh(); 
            }
        });
    }

    /**
     * Recorre la lista de UsuarioConocido y establece su estado inicial 
     * (conectado/desconectado) usando la lista global del bot.
     */
    private void updateKnownUserStatuses() {
        for (UsuarioConocido user : knownUsers) {
            // Usamos el nuevo método en el bot para una comprobación rápida y sin WHOIS.
            boolean isConnected = isUserConnected(user.getNick());
            user.setConectado(isConnected); 
        }
    }

    // 🛠️ Actualización de isUserConnected para usar el nuevo método del bot
 // Dentro de ChatController.java (El método auxiliar que falta o está mal implementado)

    private boolean isUserConnected(String nick) {
        // ⭐ CRÍTICO: Asegurarse de que la variable 'bot' esté inicializada 
        // y que el método esté usando la instancia correcta del bot.
        if (bot == null) {
            // Esto indica que la conexión falló o que el setter del bot no se llamó.
            return false; 
        }
        // ⭐ ESTO DEBE LLAMAR AL MÉTODO DEL CHATBOT QUE HACE LA CONSULTA AL SET ⭐
        return bot.isNickConnected(nick); 
    }
    /**
     * 📢 Llamado por el ChatBot cuando la sincronización inicial de nicks 
     * (JOIN/PART masivo) ha terminado. Desbloquea la UI y carga el estado inicial.
     */
 // Dentro de ChatController.java

    public void syncFinished() {
        
        // ⭐ ANTES de Platform.runLater: Llama al método de actualización del modelo de datos
        // Esto asegura que la lógica de Java (cálculo de estado) se haga primero.
        updateKnownUserStatuses(); 

        Platform.runLater(() -> {
            
            // --- 1. Notificación y Control de la UI ---
            
            appendSystemMessage("✅ Sincronización Global Finalizada. ¡Bienvenido!");

            // ⭐⭐ SOLUCIÓN 1: FORZAR EL REFRESH DE LA LISTA DE USUARIOS CONOCIDOS ⭐⭐
            // Ahora el refresh se ejecuta con el modelo de datos ya actualizado.
            if (knownUsersListView != null) { 
                 knownUsersListView.refresh(); 
                 // Debugging opcional: System.out.println("DEBUG: Refresco de knownUsersListView forzado.");
            }
            
            // ⭐⭐ SOLUCIÓN 2: ABRIR EL ACORDEÓN DE USUARIOS GLOBALES ⭐⭐
            if (mainAccordion != null && tpUsuarios != null) {
                // Expande el TitledPane que contiene la lista de usuarios conocidos.
                mainAccordion.setExpandedPane(tpUsuarios); 
            }

            // --- 2. Desbloquear la UI y habilitar la interacción. ---
            
            // Habilitar el campo de entrada (CRÍTICO)
            if (inputField != null) {
                inputField.setDisable(false);
                inputField.requestFocus(); // Dar foco al campo de entrada
            }
            
            this.isConnected = true; 
        });
    }
    
    // ------------------ CONEXIÓN IRC ------------------
    // Dentro de ChatController.java (Asegúrate de que 'server' y 'nickname' sean variables de instancia)

 // Dentro de ChatController.java

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

        // 3. Declarar variables locales (Buenas prácticas)
        final String serverToConnect = server; 
        final String nickToUse = nickname;
        
        // ⭐ Deshabilitar el input mientras la conexión está en curso y la UI está "bloqueada"
        Platform.runLater(() -> {
            // Asumiendo que tienes un método o lógica para ocultar/deshabilitar 
            // la UI principal y mostrar un mensaje de "Conectando..."
            inputField.setDisable(true);
            // [Añadir código para mostrar el indicador de progreso/bloqueo de la UI]
        });
        
        // 4. Lógica de Reconexión y Creación de ChatBot (en hilo separado)
        new Thread(() -> {
            
            // Variables FINALES locales dentro del hilo para el host/port
            String finalHost = serverToConnect;
            int finalPort = 6667; 
            
            // --- PARSEO DEL SERVIDOR DENTRO DEL HILO ---
            if (serverToConnect.contains(":")) {
                String[] parts = serverToConnect.split(":");
                finalHost = parts[0]; 
                if (parts.length > 1) {
                    try {
                        finalPort = Integer.parseInt(parts[1]); 
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
                
                appendSystemMessage("🔹 Paso 2: Listeners configurados - conectando a " + finalHost + ":" + finalPort + "...");
                
                // 5. Conectar usando las variables FINALES.
                // La conexión aquí bloqueará el hilo hasta que se establezca (o falle).
                bot.connect(finalHost, finalPort); 
                
                // ⭐ NOTA CLAVE: Después de esta línea, PircBot llama a onConnect().
                // El onConnect() del bot DEBE iniciar la sincronización de canales.

            } catch (Exception e) {
                // Si hay un error de conexión, aseguramos que la UI se desbloquee/muestre el error.
                Platform.runLater(() -> {
                    handleConnectionError(e);
                    // Asegurar que el input se deshabilita si la conexión falla.
                    inputField.setDisable(true); 
                });
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
    
 // ⭐ SETTERS REQUERIDOS POR ToolBarController
    public void setMainAccordion(Accordion mainAccordion) { 
        this.mainAccordion = mainAccordion; 
    }

    public void setTpCanales(TitledPane tpCanales) { 
        this.tpCanales = tpCanales; 
    }

    public void setTpPrivados(TitledPane tpPrivados) { 
        this.tpPrivados = tpPrivados; 
    }
    public void setVboxCanales(VBox vboxCanales) { this.vboxCanales = vboxCanales; }
    public void setVboxPrivados(VBox vboxPrivados) { this.vboxPrivados = vboxPrivados; }
    public void setVboxUsuarios(VBox vboxUsuarios) { this.vboxUsuarios = vboxUsuarios; }
    
    /**
     * Convierte la lista Observable de UsuarioConocido (desde el XML) a un Set de Strings (nicks en minúsculas).
     * Se usa para inyectar al CanalController para el resaltado verde de los conocidos.
     */
    public Set<String> getLocalKnownNicksSet() {
        // knownUsers es tu ObservableList<UsuarioConocido>
        return knownUsers.stream()
                // Asumiendo que UsuarioConocido tiene un método getNick()
                .map(u -> u.getNick().toLowerCase()) 
                .collect(java.util.stream.Collectors.toSet());
    }
    
}
