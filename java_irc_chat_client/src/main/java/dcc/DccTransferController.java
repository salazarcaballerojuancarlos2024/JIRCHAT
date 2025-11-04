package dcc;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jibble.pircbot.DccFileTransfer;
import irc.ChatBot;
import java_irc_chat_client.ChatController;
import java.io.File;
import java.io.IOException;

public class DccTransferController {

    // --- FXML Declarations ---
    @FXML private Label fileInfoLabel;
    @FXML private ProgressBar progressBarRx;
    @FXML private TextField filePathField;
    @FXML private Button acceptButton;
    @FXML private Button denyButton;
    @FXML private Label limitLabel;
    @FXML private Button btnChoosePath;
    
    private static final String TARGET_DIR = "C:\\temp\\descargas"; 
    private final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // Límite de 5MB
    
    private DccFileTransfer transfer;
    private ChatBot bot;
    private ChatController mainController; 
    private Stage stage;
    private String senderNick;

    // --- Inicialización ---

    public void initializeTransfer(ChatBot bot, DccFileTransfer transfer, ChatController mainController, Stage stage, String senderNick) {
        this.bot = bot;
        this.transfer = transfer;
        this.mainController = mainController;
        this.stage = stage;

        this.senderNick = senderNick;
        String fileName = transfer.getFile().getName();
        long fileSize = transfer.getSize();
        
        String sizeText = mainController.formatFileSize(fileSize); 

        fileInfoLabel.setText(String.format("Archivo: %s\nRemitente: %s\nTamaño: %s",
                              fileName, senderNick, sizeText));
        
        limitLabel.setText("Máximo permitido: " + mainController.formatFileSize(MAX_SIZE_BYTES));
        
        File suggestedDir = new File(TARGET_DIR); 
        suggestedDir.mkdirs();
        filePathField.setText(new File(suggestedDir, fileName).getAbsolutePath());
        
        progressBarRx.setProgress(0.0);
        progressBarRx.setVisible(false);
    }
    
    @FXML
    private void handleChoosePath() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Ubicación para Guardar " + transfer.getFile().getName());
        fileChooser.setInitialFileName(transfer.getFile().getName());

        File initialDir = new File(filePathField.getText()).getParentFile();
        if (initialDir != null && initialDir.exists()) {
            fileChooser.setInitialDirectory(initialDir);
        }

        File selectedFile = fileChooser.showSaveDialog(stage);
        
        if (selectedFile != null) {
            filePathField.setText(selectedFile.getAbsolutePath());
        }
    }

    // --- Lógica de Aceptación ---

    @FXML
    private void handleAccept() {
        File saveFile = new File(filePathField.getText());
        
        if (transfer.getSize() > MAX_SIZE_BYTES) {
            mainController.appendSystemMessage("❌ Fallo: Rechazada transferencia de " + transfer.getFile().getName() + " por superar el límite de 5 MB.");
            transfer.close();
            stage.close();
            return;
        }
        
        acceptButton.setDisable(true);
        denyButton.setDisable(true);
        btnChoosePath.setDisable(true);
        progressBarRx.setVisible(true);
        
        // El hilo de red debe iniciarse ANTES del monitoreo
        new Thread(() -> {
            try {
                transfer.receive(saveFile, false); 
                
                // Si receive() sale sin excepción, el archivo se ha transferido.
                // Llamamos al monitoreo, que detectará transferred >= total en la primera iteración.
                Platform.runLater(this::startDccProgressMonitor);
                
            } catch (Exception e) { 
                Platform.runLater(() -> {
                    mainController.appendSystemMessage("❌ Fallo de red/DCC: " + e.getMessage());
                    transfer.close();
                    stage.close();
                });
                System.err.println("Error en hilo de recepción DCC: " + e.toString()); 
            }
        }).start();

        mainController.appendSystemMessage("📥 Aceptando transferencia de " + transfer.getNick() + "...");
    }

    // --- Lógica de Rechazo ---

    @FXML
    private void handleDeny() {
        // 1. Cerrar la transferencia DCC
        if (transfer != null) {
            transfer.close();
        }
        
        // 2. Notificar a la consola principal
        mainController.appendSystemMessage("🚫 Solicitud DCC rechazada para el archivo: " + transfer.getFile().getName());
        
        // 3. ⭐ ACCIÓN CRÍTICA: Eliminar el botón del leftPane y limpiar mapas.
        // Usamos Platform.runLater por seguridad, aunque esta acción debe ocurrir en el hilo FX.
        Platform.runLater(() -> {
            if (mainController != null && senderNick != null) {
                // Llama a la función de limpieza central del ChatController
                mainController.removerBotonDcc(senderNick); 
            }
            
            // 4. Cerrar la propia ventana DCC (stage)
            if (stage != null) {
                stage.close();
            }
        });
    }

    

 // Dentro de DccTransferController.java

    private void startDccProgressMonitor() {
        
        // NOTA: Se ha ELIMINADO la lógica de Timeline y el intento de leer el progreso (getAmount, getBytesReceived, etc.)
        // La transferencia DCC ya terminó exitosamente en el hilo de red que llamó a este método.
        
        // ⭐ 1. Forzar la UI al estado final (Éxito)
        if (progressBarRx != null) {
            // Aseguramos que la barra llegue a 1.0 (100%) visualmente
            progressBarRx.setProgress(1.0); 
        }
        
        // 2. Actualizar mensaje de éxito
        String fileName = transfer.getFile().getName();
        fileInfoLabel.setText("✅ Recepción completada: " + fileName);
        mainController.appendSystemMessage("✅ Recepción de " + transfer.getNick() + " completada.");
        
        // 3. Cierre inmediato del popup (100 ms para permitir la actualización de la UI)
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override 
            public void run() { 
                Platform.runLater(() -> {
                    
                    // ⭐ ACCIÓN CLAVE: Eliminar el botón DCC del leftPane
                    if (mainController != null && senderNick != null) {
                        mainController.removerBotonDcc(senderNick); 
                    }
                    
                    // Cerrar la ventana Stage
                    if (stage != null) {
                        stage.close();
                    }
                }); 
            }
        }, 100); 
    }
}

    