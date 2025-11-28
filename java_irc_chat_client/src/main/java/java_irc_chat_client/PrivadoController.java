package java_irc_chat_client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.jibble.pircbot.DccFileTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Nuevos imports para DCC y Drag&Drop
import java.io.File;
import java.util.function.BiConsumer;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.control.ProgressBar; 

import irc.ChatBot; 

public class PrivadoController {

    private static final Logger log = LoggerFactory.getLogger(PrivadoController.class);

    @FXML private BorderPane rootPane;
    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScrollPane;
    @FXML private TextField inputField_privado;
    @FXML private ProgressBar progressBarDcc; // ⭐ Componente inyectado desde FXML

    private ChatBot bot; 
    private String destinatario;
    private ChatController mainController;
    private SymbolMapper symbolMapper;
    // La clase ChatLogger debe existir en tu proyecto
    
    // ==========================================================
    // CONFIGURACIÓN INICIAL
    // ==========================================================
    public void setBot(ChatBot bot) { this.bot = bot; } 
    public void setDestinatario(String nick) { this.destinatario = nick; }
    public void setMainController(ChatController mainController) { this.mainController = mainController; }
    public BorderPane getRootPane() { return rootPane; }

    @FXML
    public void initialize() {
        symbolMapper = new SymbolMapper();
        inputField_privado.setOnAction(e -> sendMessage());
        
        setupDragAndDrop(); // ⭐ Configurar el manejo de arrastrar y soltar
        
        // Aseguramos que la barra de progreso esté oculta al inicio.
        if (progressBarDcc != null) {
            progressBarDcc.setVisible(false); 
        }
    }

    // ==========================================================
    // DRAG & DROP Y ENVÍO DCC (Emisor)
    // ==========================================================
    private void setupDragAndDrop() {
        // 1. Manejo del arrastre
        rootPane.setOnDragOver(event -> {
            if (event.getGestureSource() != rootPane && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        // 2. Manejo de la soltura (Drop)
        rootPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File file = db.getFiles().get(0); // Tomar solo el primer archivo
                
                // ⭐ RESTRICCIÓN DE TAMAÑO (5 Mbytes)
                final long MAX_SIZE_BYTES = 5 * 1024 * 1024;
                
                if (file.length() > MAX_SIZE_BYTES) {
                    mainController.appendSystemMessage("⚠️ Fallo: El archivo " + file.getName() + " supera el límite de 5 MB (" + MAX_SIZE_BYTES / 1024 / 1024 + " MB).");
                } else {
                    iniciarEnvioArchivo(file);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

 // Clase ChatController.java (MÉTODO REGENERADO)
    public void iniciarEnvioArchivo(File archivo) {
        if (bot == null || destinatario == null) {
            appendSystemMessage("❌ Error: No se puede iniciar la transferencia DCC. Bot no conectado o destinatario no válido.");
            return;
        }
        
        final String targetNick = destinatario; // Almacenamos el destinatario para el logger/error
        final String fileName = archivo.getName();
        
        // 1. Preparar la UI para el envío
        if (progressBarDcc != null) {
            progressBarDcc.setProgress(0.0);
            progressBarDcc.setVisible(true);
        }

        // 2. Registrar el callback de progreso antes de iniciar el envío
        // Asumimos que registerDccProgressConsumer está implementado en tu ChatBot para esta versión.
        bot.registerDccProgressConsumer(targetNick, getDccProgressConsumer(archivo));
        
        // 3. Iniciar el envío en un nuevo Thread (para no bloquear la UI)
        new Thread(() -> {
            try {
                // Notificar a la UI que la solicitud se está enviando
                Platform.runLater(() -> {
                    appendSystemMessage("📨 Solicitando transferencia DCC de: " + fileName + " a " + targetNick);
                });
                
                // LÍNEA CRÍTICA: Iniciar el envío DCC. 120000ms (2 minutos) de timeout.
                bot.sendDccFile(archivo, targetNick, 120000); 
                
                // El resto de la notificación de éxito la gestiona el getDccProgressConsumer 
                // cuando (transferred >= total).

            } catch (Exception e) {
                // ⭐ TRATAMIENTO DE ERRORES COMPLETO: Notificar y limpiar la UI.
                Platform.runLater(() -> {
                    String errorMsg = "❌ Fallo al enviar " + fileName + " a " + targetNick + ". Error: " + e.getMessage();
                    appendSystemMessage(errorMsg);
                    
                    // Ocultar barra de progreso y limpiarla
                    if (progressBarDcc != null) {
                        progressBarDcc.setProgress(0.0);
                        progressBarDcc.setVisible(false);
                    }
                });
                // Registro del error para depuración
                System.err.println("Error al iniciar/ejecutar DCC Send: " + e.toString());
            }
        }).start();
    }

    // LÓGICA DE ACTUALIZACIÓN DE BARRA DE PROGRESO (Lado Emisor)
    private BiConsumer<Long, Long> getDccProgressConsumer(File file) {
        final String fileName = file.getName();
        return (transferred, total) -> {
            // Se debe ejecutar en el hilo de JavaFX para actualizar la UI
            Platform.runLater(() -> {
                if (progressBarDcc == null) return;
                
                double progress = (total > 0) ? (double) transferred / total : 0.0;
                progressBarDcc.setProgress(progress);
                
                if (transferred >= total && total > 0) {
                    appendSystemMessage("✅ Transferencia DCC de " + fileName + " completada.");
                    progressBarDcc.setVisible(false);
                }
            });
        };
    }
    
 // Dentro de PrivadoController.java

    /**
     * Muestra una línea de texto plano directamente en el chatBox.
     * Se usa SOLAMENTE para cargar el historial desde el archivo.
     * NO llama a ChatLogger.log().
     */
    public void appendRawLogLine(String linea) {
        Platform.runLater(() -> {
            if (chatBox == null) return;
            
            // Creamos un solo TextFlow para la línea completa del log.
            // Esto asegura que la línea se muestre exactamente como está en el archivo.
            TextFlow flow = new TextFlow(new Text(linea));
            flow.setStyle("-fx-padding: 0 0 0 0;"); // Estilo mínimo
            
            chatBox.getChildren().add(flow);
            autoScroll();
        });
    }

    // ==========================================================
    // MENSAJES DE TEXTO
    // ==========================================================
    private void sendMessage() {
        String text = inputField_privado.getText().trim();
        if (text.isEmpty() || bot == null || destinatario == null) return;

        // 1. Enviar mensaje por IRC
        bot.sendMessage(destinatario, text); 

        // 2. Mostrar localmente
        // En el chat privado, nuestro propio nick es el "usuario" y el destinatario es el log.
        String myNick = bot.getNick(); // Asumimos que puedes obtener tu nick del bot

        appendMessage(myNick, text); // Usamos nuestro propio nick para la visualización

        // ⭐ PUNTO DE LOGGING 1: MENSAJE SALIENTE (MÍO)
        // El nombre del log es el nick del destinatario.
        ChatLogger.log(destinatario, myNick, text); 
        // -----------------------------------------------------

        inputField_privado.clear();
    }
    
public void appendMessage(String usuario, String mensaje) {
        
        
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