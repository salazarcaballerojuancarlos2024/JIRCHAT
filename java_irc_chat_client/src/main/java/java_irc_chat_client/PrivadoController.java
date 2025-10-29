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
        
        // ⭐ Mover las actualizaciones de UI al Hilo de JavaFX (Correcto)
        if (progressBarDcc != null) {
            progressBarDcc.setProgress(0.0);
            progressBarDcc.setVisible(true);
            // La actualización de la barra de progreso se registra después
        }

        // 2. Registrar el callback de progreso para este destinatario
        bot.registerDccProgressConsumer(destinatario, getDccProgressConsumer(archivo));
        
     // Código Correcto para el Emisor (dentro del nuevo Thread que creaste)
        new Thread(() -> {
            try {
                // 1. Iniciar el envío DCC
                // Ya NO asignamos el resultado a una variable
                bot.sendDccFile(archivo, destinatario, 120000); 
                
                // El objeto DccFileTransfer se crea internamente y se puede acceder más tarde
                // si fuera necesario, pero la forma en que registraste el consumidor ya lo maneja.

                // 2. Notificar a la UI
                Platform.runLater(() -> {
                    appendSystemMessage("📨 Solicitando transferencia DCC de: " + archivo.getName() + " a " + destinatario);
                });

                // NOTA: Si necesitas llamar a setPacketDelay, no puedes hacerlo directamente aquí.
                // PircBot aplica el packet delay a través de una propiedad global o una clase interna.
                // Si necesitas aplicar el fix de 50ms, debes hacerlo antes de llamar a sendDccFile
                // usando el método setOutgoingDccPacketDelay(50) si PircBot lo expone, o
                // aceptar que el logger de 50ms ya lo está aplicando.

            } catch (Exception e) {
                // ... (Manejo de errores) ...
            }
        }).start();
    }
    
    // ⭐ LÓGICA DE ACTUALIZACIÓN DE BARRA DE PROGRESO (Lado Emisor)
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

    // ==========================================================
    // MENSAJES DE TEXTO
    // ==========================================================
    private void sendMessage() {
        String text = inputField_privado.getText().trim();
        if (text.isEmpty() || bot == null || destinatario == null) return;

        bot.sendMessage(destinatario, text); 

        // Mostrar localmente
        appendMessage("Yo", text);

        // Guardar en log (Asumiendo que ChatLogger existe)
        // ChatLogger.log(destinatario, "Yo", text);

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