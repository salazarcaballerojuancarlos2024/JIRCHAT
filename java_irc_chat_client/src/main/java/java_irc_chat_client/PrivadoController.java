package java_irc_chat_client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ELIMINADO: import java.io.File; // Ya no es necesario

// ⭐ ADAPTACIÓN: Usamos PircBot (la clase base) y nuestra ChatBot
import org.jibble.pircbot.PircBot;
import irc.ChatBot; 
// ELIMINADO: import dcc.TransferManager; 

public class PrivadoController {

    private static final Logger log = LoggerFactory.getLogger(PrivadoController.class);

    @FXML private BorderPane rootPane;
    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScrollPane;
    @FXML private TextField inputField_privado;

    // ⭐ ADAPTACIÓN: Cambiamos el tipo a ChatBot
    private ChatBot bot; 
    private String destinatario;
    private ChatController mainController;
    private SymbolMapper symbolMapper;
    // ELIMINADO: private TransferManager TransferManager; // Ya no es necesario

    // ==========================================================
    // CONFIGURACIÓN INICIAL
    // ==========================================================
    // ⭐ ADAPTACIÓN: setBot ahora acepta ChatBot
    public void setBot(ChatBot bot) { this.bot = bot; } 
    public void setDestinatario(String nick) { this.destinatario = nick; }
    public void setMainController(ChatController mainController) { this.mainController = mainController; }
    // ELIMINADO: public void setTransferManager(TransferManager TransferManager) { this.TransferManager = TransferManager; }
    public BorderPane getRootPane() { return rootPane; }

    @FXML
    public void initialize() {
        symbolMapper = new SymbolMapper();
        inputField_privado.setOnAction(e -> sendMessage());

        // ELIMINADO: setupDragAndDrop();
    }

    // ==========================================================
    // DRAG & DROP Y ENVÍO DE ARCHIVOS (ELIMINADO)
    // ==========================================================
    // ELIMINADO: private void setupDragAndDrop() { ... }
    // ELIMINADO: public void iniciarEnvioArchivo(File archivo) { ... }

    // ==========================================================
    // MENSAJES DE TEXTO
    // ==========================================================
    private void sendMessage() {
        String text = inputField_privado.getText().trim();
        if (text.isEmpty() || bot == null || destinatario == null) return;

        // ⭐ ADAPTACIÓN: PircBot 1.5.0 usa bot.sendMessage(nick, message);
        bot.sendMessage(destinatario, text); 

        // Mostrar localmente
        appendMessage("Yo", text);

        // Guardar en log
        ChatLogger.log(destinatario, "Yo", text);

        inputField_privado.clear();
    }
    
    public void appendMessage(String usuario, String mensaje) {
        // ... (se mantiene igual) ...
        Platform.runLater(() -> {
            Text userText = new Text("<" + usuario + "> ");
            userText.setFont(Font.font("Segoe UI Emoji", FontWeight.BOLD, 14));
            userText.setFill(Color.BLACK);

            TextFlow messageFlow = parseIRCMessage(mensaje);

            TextFlow fullFlow = new TextFlow(userText);
            fullFlow.getChildren().addAll(messageFlow.getChildren());

            chatBox.getChildren().add(fullFlow);
            autoScroll();
        });
    }
    
    public void appendSystemMessage(String mensaje) {
        // ... (se mantiene igual) ...
        Platform.runLater(() -> {
            TextFlow messageFlow = parseIRCMessage(mensaje);

            for (var t : messageFlow.getChildren()) {
                ((Text) t).setFill(Color.GRAY);
                ((Text) t).setFont(Font.font("Segoe UI Emoji", FontWeight.NORMAL, 13));
                ((Text) t).setStyle("-fx-font-style: italic;");
            }

            chatBox.getChildren().add(messageFlow);
            autoScroll();
        });
    }

    private TextFlow parseIRCMessage(String mensaje) {
        // ... (se mantiene igual) ...
        TextFlow flow = new TextFlow();
        int i = 0;
        Color currentColor = Color.BLACK;
        boolean bold = false;

        while (i < mensaje.length()) {
            char c = mensaje.charAt(i);
            if (c == '\u0003') { // Código de color IRC
                i++;
                StringBuilder num = new StringBuilder();
                while (i < mensaje.length() && Character.isDigit(mensaje.charAt(i))) 
                    num.append(mensaje.charAt(i++));
                try { 
                    currentColor = ircColorToFX(Integer.parseInt(num.toString())); 
                } catch (Exception e) { 
                    currentColor = Color.BLACK; 
                }
            } else if (c == '\u000F') { // Reset
                currentColor = Color.BLACK;
                bold = false;
                i++;
            } else if (c == '\u0002') { // Negrita toggle
                bold = !bold;
                i++;
            } else {
                int codePoint = mensaje.codePointAt(i);
                String mapped = symbolMapper.mapChar((char) codePoint);
                Text t = new Text(mapped);
                t.setFill(currentColor);
                t.setFont(Font.font("Segoe UI Emoji", bold ? FontWeight.BOLD : FontWeight.NORMAL, 14));
                flow.getChildren().add(t);

                i += Character.charCount(codePoint);
            }
        }

        return flow;
    }

    private Color ircColorToFX(int code) {
        return switch (code) {
            case 0 -> Color.WHITE; 
            case 1 -> Color.BLACK; 
            case 2 -> Color.DODGERBLUE;
            case 3 -> Color.LIMEGREEN; 
            case 4 -> Color.RED; 
            case 5 -> Color.SADDLEBROWN;
            case 6 -> Color.MEDIUMPURPLE; 
            case 7 -> Color.ORANGE; 
            case 8 -> Color.GOLD;
            case 9 -> Color.GREEN; 
            case 10 -> Color.CYAN; 
            case 11 -> Color.TURQUOISE;
            case 12 -> Color.ROYALBLUE; 
            case 13 -> Color.HOTPINK; 
            case 14 -> Color.DARKGREY; 
            case 15 -> Color.LIGHTGREY;
            default -> Color.BLACK;
        };
    }

    // ==========================================================
    // UTILIDADES DE UI
    // ==========================================================
    private void autoScroll() {
        chatBox.layout();
        chatScrollPane.layout();
        chatScrollPane.setVvalue(1.0);
    }

    public void initializeChat() {
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    
}