package java_irc_chat_client;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import irc.ChatBot;
import org.jibble.pircbot.PircBot; // Importado pero no usado directamente en esta clase

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CanalController {

    // --- FXML Declarations (Adjusted Names) ---
    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScrollPane;
    @FXML private TextField inputField_canal;
    @FXML private ListView<String> userListView_canal; // Tu FXML ID
    @FXML private AnchorPane popupPane;
    @FXML private Label labelUserCount;
    
    // --- Lógica de Estado y Datos ---
    
    // ⭐ CORRECCIÓN CLAVE: Esta es la lista que usan todos los métodos de usuario.
    // Renombrada de 'users' a 'usersList' para coincidir con el código que generaste.
    private final ObservableList<String> usersList = FXCollections.observableArrayList();
    
    private final List<String> nickCache = new ArrayList<>();
    private final List<String> currentMatches = new ArrayList<>();
    private int matchIndex = -1;
    private String lastPrefix = null;
    private Set<String> knownNicks = Collections.emptySet();

    private List<String> currentNamesList = new ArrayList<>();
    private boolean receivingNames = false;

    // ⭐ CORRECCIÓN: Usamos solo 'channelName' y lo inicializamos con el setter
    private String channelName;
    private static final Logger log = LoggerFactory.getLogger(CanalController.class); // Log para esta clase

    private ChatBot bot; 
    private ChatController mainController;
    private Consumer<String> onUserDoubleClick;
 
    // --- Setters (Adaptados a 'channelName') ---

    public void setBot(ChatBot bot) { this.bot = bot; } 
    public void setCanal(String channelName) { this.channelName = channelName; }
    public void setMainController(ChatController mainController) { this.mainController = mainController; }
    public void setUserDoubleClickHandler(Consumer<String> handler) { this.onUserDoubleClick = handler; }
    
    // --- Lógica de Manejo de Servidor ---
    
    /**
     * Añade un nuevo usuario a la lista local y actualiza el contador.
     * Este método es llamado por ChatBot.onJoin (cuando otro usuario se une).
     * @param nick El nick del usuario que se unió (sin prefijos de modo en el evento JOIN).
     */
    public void addUserToList(String nick) {
        Platform.runLater(() -> {
            // 1. Verificar si el usuario ya está en la lista (para evitar duplicados)
            boolean exists = usersList.stream()
                .anyMatch(userInList -> userInList.replaceFirst("[@+]", "").equalsIgnoreCase(nick));

            if (!exists) {
                // 2. Añadir el nuevo nick a la lista. 
                // Lo añadimos sin prefijos, la lógica de 'updateUsers' manejará el orden.
                usersList.add(nick); 

                // 3. Preparar la lista actual de usuarios (excluyendo el contador)
                List<String> currentUsers = new ArrayList<>(usersList);
                if (!currentUsers.isEmpty() && currentUsers.get(0).startsWith("Usuarios:")) {
                    currentUsers.remove(0); // Quita el contador del principio de la lista.
                }

                // 4. ⭐ CRÍTICO: Llamar a updateUsers(List<String>)
                // Este método se encarga de reordenar la lista y actualizar el Label del contador (labelUserCount).
                updateUsers(currentUsers);
            }
        });
    }
    
    public void handleNamesResponse(int code, String response) {
        if (code == 353) {
            if (!receivingNames) {
                currentNamesList.clear();
                receivingNames = true;
            }
            
            int colonIndex = response.lastIndexOf(':');
            if (colonIndex != -1) {
                String nicks = response.substring(colonIndex + 1);
                String[] nickArray = nicks.trim().split(" ");
                
                for (String nick : nickArray) {
                    if (!nick.isEmpty()) {
                        currentNamesList.add(nick);
                    }
                }
            }
            
        } else if (code == 366) { 
            receivingNames = false;
            
            log.debug("Lista de usuarios recibida para el canal: {}", this.channelName);
            updateUsers(currentNamesList); 
            
            // Opcional: Desregistro del delegado (si el bot lo soporta y es necesario)
            // if (bot != null) bot.registerNamesDelegate(channelName, null); 
        }
    }
    
    /**
     * ⭐ MÉTODO CORREGIDO: Elimina un usuario de la lista local (usado en onPart).
     * @param nick El nick del usuario que ha salido.
     */
    public void removeUserFromList(String nick) {
        // La lista 'usersList' es ahora accesible
        boolean removed = usersList.removeIf(userInList -> 
            // Compara si el nick de la lista (con prefijos @/+) coincide con el nick del evento (sin prefijos)
            userInList.replaceFirst("[@+]", "").equalsIgnoreCase(nick)
        );

        if (removed) {
            // Se asume que el contador es el primer elemento de la lista.
            if (!usersList.isEmpty() && usersList.get(0).startsWith("Usuarios:")) {
                // Hay que actualizar el contador en el primer elemento o en el labelUserCount
                int currentCount = usersList.size() - 1; // -1 porque el primer elemento es el contador
                usersList.set(0, "Usuarios: " + currentCount); // Actualiza el contador en la lista
            }
            // También actualiza la etiqueta del contador por si acaso
            updateUserCount(usersList.size() - 1);
        }
    }

    // --- Inicialización y UI ---

    @FXML
    public void initialize() {
       
        if (chatBox != null) chatBox.setStyle("-fx-background-color: #FFF8DC;");
        // ⭐ CORRECCIÓN: Vincula la lista renombrada.
        userListView_canal.setItems(usersList);

        // ListView: filas alternas y usuarios conocidos
        userListView_canal.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    // ⭐ CORRECCIÓN: Verifica el primer elemento para el contador
                    if (item.startsWith("Usuarios:")) { setStyle("-fx-background-color: transparent; -fx-font-weight: bold;"); return; }

                    String baseStyle = getIndex() % 2 == 0
                            ? "-fx-background-color: #FFFACD; -fx-font-weight: bold;"
                            : "-fx-background-color: #ADD8E6; -fx-font-weight: bold;";

                    String clean = item.startsWith("@") || item.startsWith("+") ? item.substring(1) : item;
                    boolean esConocido = knownNicks.contains(clean.toLowerCase());
                    setStyle(esConocido ? "-fx-background-color: #39FF14; -fx-text-fill: black; -fx-font-weight: bold;" : baseStyle);
                }
            }
        });

        if (inputField_canal != null) {
            inputField_canal.setOnAction(e -> sendCommand());
            inputField_canal.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.TAB) { event.consume(); handleTabCompletion(event.isShiftDown()); }
            });
        }

        // Doble click abre chat privado
        userListView_canal.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedUser = userListView_canal.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.startsWith("Usuarios:")) {
                    String nick = selectedUser.startsWith("@") || selectedUser.startsWith("+") ? selectedUser.substring(1) : selectedUser;
                    
                    abrirChatPrivado(nick);
                    inputField_canal.setText("/msg " + nick + " ");
                    inputField_canal.positionCaret(inputField_canal.getText().length());
                    if (onUserDoubleClick != null) onUserDoubleClick.accept(nick);
                }
            }
        });
    }

    // --- Métodos de Lógica de Chat ---

    private void handleTabCompletion(boolean reverse) {
        String text = inputField_canal.getText();
        int caretPos = inputField_canal.getCaretPosition();
        int start = Math.max(0, text.lastIndexOf(' ', caretPos - 1) + 1);
        String prefix = text.substring(start, caretPos).trim();
        if (prefix.isEmpty()) return;

        if (!prefix.equalsIgnoreCase(lastPrefix)) {
            currentMatches.clear();
            // ⭐ CORRECCIÓN: Usar usersList para la autocompleción
            for (String nick : usersList) { 
                if (nick == null || nick.startsWith("Usuarios:")) continue;
                String cleanNick = nick.startsWith("@") || nick.startsWith("+") ? nick.substring(1) : nick;
                if (cleanNick.toLowerCase().startsWith(prefix.toLowerCase())) currentMatches.add(nick);
            }
            currentMatches.sort(String.CASE_INSENSITIVE_ORDER);
            lastPrefix = prefix;
            matchIndex = -1;
        }

        if (currentMatches.isEmpty()) return;
        matchIndex = reverse ? (matchIndex - 1 + currentMatches.size()) % currentMatches.size()
                             : (matchIndex + 1) % currentMatches.size();
        String replacement = currentMatches.get(matchIndex);
        inputField_canal.setText(text.substring(0, start) + replacement + text.substring(caretPos));
        inputField_canal.positionCaret(start + replacement.length());
    }

    private void sendCommand() {
        String text = inputField_canal.getText().trim();
        if (text.isEmpty() || bot == null) return;
        try {
            if (text.startsWith("/")) handleCommand(text.substring(1).trim());
            else sendMessageToChannel(text);
        } finally {
            inputField_canal.clear();
            lastPrefix = null;
            currentMatches.clear();
            matchIndex = -1;
        }
    }

    private void handleCommand(String cmd) {
        if (bot == null) return;

        if (cmd.startsWith("part")) {
            bot.partChannel(channelName); // Usar channelName
            appendSystemMessage("➡ Saliendo de " + channelName, MessageType.PART, bot.getNick());
            if (mainController != null) Platform.runLater(() -> mainController.cerrarCanalDesdeVentana(channelName));
        } else if (cmd.startsWith("msg ")) {
            String[] parts = cmd.split(" ", 3);
            if (parts.length >= 3) {
                bot.sendMessage(parts[1], parts[2]); 
            }
        } else if (cmd.startsWith("me ")) {
            bot.sendAction(channelName, cmd.substring(3).trim()); // Usar channelName
        } else {
            bot.sendRawLine(cmd); 
        }
    }

    public void sendMessageToChannel(String msg) {
        if (bot != null && channelName != null) {
            bot.sendMessage(channelName, msg);
            appendMessage("Yo", msg);
        }
    }

    // --- Métodos de Actualización de Usuarios ---

    public void updateUsers(List<String> userList) {
        Platform.runLater(() -> {
            // --- Lógica de KnownNicks (Se mantiene) ---
            try { 
                this.knownNicks = Collections.emptySet(); 
            } 
            catch (Exception e) { 
                this.knownNicks = Collections.emptySet(); 
            }

            // --- PREPARACIÓN ---
            List<String> validUsers = userList.stream()
                    .filter(u -> u != null && !u.trim().isEmpty())
                    .sorted(this::compareNicks)
                    .collect(Collectors.toList());

            // --- ACTUALIZACIÓN DE LA LISTA OBSERVABLE ---
            this.usersList.clear();                       // 1. Limpia
            this.nickCache.clear();
            
            this.usersList.add("Usuarios: " + validUsers.size()); // 2. Añade el contador
            this.usersList.addAll(validUsers);                     // 3. Añade los usuarios

            // --- LÓGICA DE CACHÉ Y CONTADOR ---
            for (String u : validUsers) nickCache.add(u.startsWith("@") || u.startsWith("+") ? u.substring(1) : u);
            
            updateUserCount(validUsers.size());
        });
    }

    // --- Métodos Auxiliares ---
    
    private int compareNicks(String a, String b) {
        int rankA = getNickRank(a);
        int rankB = getNickRank(b);
        if (rankA != rankB) return Integer.compare(rankA, rankB);
        String cleanA = a.startsWith("@") || a.startsWith("+") ? a.substring(1) : a;
        String cleanB = b.startsWith("@") || b.startsWith("+") ? b.substring(1) : b;
        return cleanA.compareToIgnoreCase(cleanB);
    }
    
    public void updateUserCount(int count) {
        if (labelUserCount != null) {
            Platform.runLater(() -> labelUserCount.setText("Usuarios conectados: " + count));
        }
    }

    private int getNickRank(String nick) {
        if (nick.startsWith("@")) return 0; // Operator
        if (nick.startsWith("+")) return 1; // Voice
        char c = nick.charAt(0);
        if (!Character.isLetterOrDigit(c)) return 2;
        return 3; // Regular user
    }

    public void abrirChatPrivado(String nick) {
        if (mainController != null) mainController.abrirChatPrivado(nick);
    }

    public String getCanal() { return channelName; } // Usar channelName

    public enum MessageType { JOIN, PART, KICK }

    // --- Métodos de UI (appendMessage, appendSystemMessage, autoScroll, parseIRCMessage, ircColorToFX) ---
    
 // En CanalController.java

    /**
     * Muestra un mensaje de chat normal (<Usuario> Mensaje) en el VBox.
     * CORREGIDO: Usa chatBox y la lógica de TextFlow.
     */
    public void appendMessage(String usuario, String mensaje) {
        Platform.runLater(() -> {
            // Usa el campo FXML declarado en la clase
            if (chatBox == null) return; 

            TextFlow flow = new TextFlow();
            Text userText = new Text("<" + usuario + "> ");
            
            // Estilo del usuario
            userText.setFill(Color.DARKBLUE);
            userText.setFont(Font.font("System", FontWeight.BOLD, 12));
            
            flow.getChildren().add(userText);
            
            // Añade el cuerpo del mensaje parseado (colores/formato IRC)
            flow.getChildren().addAll(parseIRCMessage(mensaje).getChildren());
            
            // Añadir el mensaje al VBox
            chatBox.getChildren().add(flow);
            
            // Auto-scroll
            autoScroll(); 
        });
    }

    /**
     * Muestra mensajes del sistema (JOIN, PART, KICK) en la UI.
     */
    public void appendSystemMessage(String mensaje, MessageType type, String nickSalida) {
        Platform.runLater(() -> {
            if (chatBox == null) return; 

            TextFlow flow = new TextFlow();
            flow.setStyle("-fx-background-color: #FFF8DC; -fx-padding: 3px;");
            Text prefix = new Text("___________________________> ");
            Text body = new Text(mensaje);
            Text suffix = new Text(" <___________________________");

            // Asignar color basado en el tipo de evento
            switch (type) {
                case JOIN -> body.setFill(Color.web("#009300")); // Verde
                case PART -> body.setFill(Color.web("#FC7F00")); // Naranja
                case KICK -> body.setFill(Color.web("#FF0000")); // Rojo
            }

            prefix.setFill(Color.DARKGRAY); suffix.setFill(Color.DARKGRAY);
            prefix.setFont(Font.font("System", FontWeight.NORMAL, 12));
            body.setFont(Font.font("System", FontWeight.BOLD, 12));
            suffix.setFont(Font.font("System", FontWeight.NORMAL, 12));

            flow.getChildren().addAll(prefix, body, suffix);
            flow.setLineSpacing(1.2);
            chatBox.getChildren().add(flow);
            autoScroll();

            // Muestra popup al salir (si es un evento PART y el usuario salió)
            if (type == MessageType.PART && nickSalida != null) showExitPopup(nickSalida, channelName);
        });
    }

    /**
     * Realiza el auto-scroll en el área de chat.
     */
    private void autoScroll() {
        // Usa el campo FXML para el contenedor principal
        if (chatScrollPane != null) { 
            chatBox.layout(); 
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0); // Desplaza al final
        }
    }
 // En CanalController.java

    /**
     * Analiza un mensaje de IRC para aplicar códigos de color y formato (bold).
     */
    private TextFlow parseIRCMessage(String mensaje) {
        TextFlow flow = new TextFlow();
        Color currentColor = Color.BLACK;
        boolean bold = false;
        int i = 0;
        while (i < mensaje.length()) {
            char c = mensaje.charAt(i);
            if (c == '\u0003') { // Código de color (Control-C)
                i++;
                StringBuilder num = new StringBuilder();
                // Leer el código de color
                while (i < mensaje.length() && Character.isDigit(mensaje.charAt(i))) num.append(mensaje.charAt(i++));
                try { currentColor = ircColorToFX(Integer.parseInt(num.toString())); }
                catch (Exception e) { currentColor = Color.BLACK; }
            } else if (c == '\u000F') { // Reinicio de formato
                currentColor = Color.BLACK; 
                bold = false; 
                i++; 
            } else if (c == '\u0002') { // Negrita (Bold)
                bold = !bold; 
                i++; 
            } else {
                // Añadir carácter normal
                int codePoint = mensaje.codePointAt(i);
                Text t = new Text(new String(Character.toChars(codePoint)));
                t.setFill(currentColor);
                t.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, 12));
                flow.getChildren().add(t);
                i += Character.charCount(codePoint);
            }
        }
        return flow;
    }

    /**
     * Mapea el código de color numérico de IRC a un objeto Color de JavaFX.
     */
    private Color ircColorToFX(int code) {
        return switch (code) {
            case 0 -> Color.WHITE; case 1 -> Color.BLACK; case 2 -> Color.BLUE;
            case 3 -> Color.GREEN; case 4 -> Color.RED; case 5 -> Color.BROWN;
            case 6 -> Color.PURPLE; case 7 -> Color.ORANGE; case 8 -> Color.YELLOW;
            case 9 -> Color.LIGHTGREEN; case 10 -> Color.CYAN; case 11 -> Color.TURQUOISE;
            case 12 -> Color.DARKBLUE; case 13 -> Color.PINK; case 14 -> Color.GRAY; case 15 -> Color.LIGHTGRAY;
            default -> Color.BLACK;
        };
    }

    // --- Métodos de Popup (showExitPopup, crearPopup) ---

 // En CanalController.java

    /**
     * Muestra un popup de notificación de que un usuario quiere chatear en privado.
     */
    public void onPrivateMessageRequest(String nickRemoto) {
        Platform.runLater(() -> {
            if (popupPane == null) return;

            VBox container = crearPopup(nickRemoto + " quiere chatear en privado. Aceptar?", Color.BEIGE);
            popupPane.getChildren().add(container);
            container.toFront();
        });
    }

    /**
     * Muestra un popup cuando un usuario sale o entra del canal.
     */
    public void showExitPopup(String nick, String canal) {
        Platform.runLater(() -> {
            if (popupPane == null) return;

            VBox container = crearPopup(nick + " sale de " + canal, Color.BEIGE);
            popupPane.getChildren().add(container);
            container.toFront();
        });
    }

    /**
     * Crea la burbuja de popup animada.
     */
    private VBox crearPopup(String mensaje, Color color) {
        StackPane bubble = new StackPane();
        bubble.setBackground(new Background(new BackgroundFill(color, new CornerRadii(10), Insets.EMPTY)));
        bubble.setPadding(new Insets(10));
        bubble.setPrefWidth(200);

        Label label = new Label(mensaje);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");
        label.setWrapText(true);
        label.setMaxWidth(180);
        bubble.getChildren().add(label);

        // Triángulo que apunta hacia abajo
        Polygon triangle = new Polygon(0.0, 0.0, 10.0, 0.0, 5.0, 10.0);
        triangle.setFill(color);

        VBox container = new VBox(bubble, triangle);
        container.setSpacing(0);

        // Posicionamiento
        double popupWidth = 200;
        // Asumimos que popupPane tiene un tamaño definido
        Platform.runLater(() -> {
            container.setLayoutX((popupPane.getWidth() - popupWidth) / 2);
        });
        container.setLayoutY(20);

        // Animaciones de movimiento y desvanecimiento
        TranslateTransition tt = new TranslateTransition(Duration.seconds(5), container);
        tt.setByY(-50); // sube

        FadeTransition ft = new FadeTransition(Duration.seconds(5), container);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);

        tt.play();
        ft.play();

        ft.setOnFinished(e -> popupPane.getChildren().remove(container));
        return container;
    }

} // Fin de la clase CanalController