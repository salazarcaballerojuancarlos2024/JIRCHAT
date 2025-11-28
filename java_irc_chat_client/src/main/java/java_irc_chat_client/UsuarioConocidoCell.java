package java_irc_chat_client;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.paint.Color; // No necesario si usas -fx-background-color

public class UsuarioConocidoCell extends ListCell<UsuarioConocido> {

    // --- Componentes Fijos de la Celda ---
    private static final String ICON_PATH = "/java_irc_chat_client/icons/icons8-usuario-de-género-neutro-50.png";
    private final ImageView imageView = new ImageView();
    private final HBox content = new HBox(5); // Contenedor para icono y texto
    private final Label label = new Label();
    private final Image userIcon;
    
    // --- Estilos de la Celda ---
    // Color verde fosforito (Neon Green)
    private static final String CONECTADO_STYLE = "-fx-background-color: #39FF14; -fx-background-radius: 5; -fx-text-fill: black;"; 
    private static final String DESCONECTADO_STYLE = "";
    
    // --- Listener de Propiedad ---
    // Listener que se reutilizará para monitorear la propiedad 'conectado'
    private final ChangeListener<Boolean> conectadoListener = (obs, oldVal, newVal) -> {
        // Aseguramos que la manipulación de la UI se haga en el hilo de JavaFX
        Platform.runLater(() -> updateStyle(newVal));
    };

    /**
     * Constructor de la celda. Inicializa componentes gráficos.
     */
    public UsuarioConocidoCell() {
        // 1. Cargar y configurar el icono (Solo se hace una vez)
        userIcon = new Image(getClass().getResourceAsStream(ICON_PATH));
        imageView.setImage(userIcon);
        imageView.setFitHeight(16);
        imageView.setFitWidth(16);

        // 2. Configurar el HBox
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(imageView, label);
        
        // El label se llenará en updateItem (o por binding si se prefiere).
    }
    
    /**
     * Lógica para aplicar/quitar el estilo de conectado.
     */
    private void updateStyle(boolean isConnected) {
        if (isConnected) {
            setStyle(CONECTADO_STYLE);
        } else {
            setStyle(DESCONECTADO_STYLE);
        }
    }

    /**
     * Método principal llamado por el ListView para actualizar el contenido de la celda.
     */
    @Override
    protected void updateItem(UsuarioConocido user, boolean empty) {
        // 1. NECESARIO: Llamar al método base primero
        super.updateItem(user, empty);

        // 2. ⭐ DESVINCULAR LISTENER DEL ITEM ANTERIOR ⭐ (Evita fugas de memoria)
        if (getItem() != null) {
            getItem().conectadoProperty().removeListener(conectadoListener);
        }

        if (empty || user == null) {
            // Caso de celda vacía o sin objeto
            setText(null);
            setGraphic(null);
            setStyle(DESCONECTADO_STYLE); // Asegurar que no quede sombreado
        } else {
            // Caso de celda con un objeto UsuarioConocido válido
            
            // 3. Establecer el texto
            // Usaremos el texto directamente en lugar de un binding complejo para el Label,
            // ya que el nickname es estático.
        	label.setText(user.getNick());
            
            // 4. ⭐ VINCULAR LISTENER AL NUEVO ITEM ⭐
            user.conectadoProperty().addListener(conectadoListener);
            
            // 5. Aplicar el estilo INICIAL basado en el estado actual del nuevo item
            updateStyle(user.isConectado());
            
            // 6. Configurar el gráfico de la celda
            setGraphic(content);
        }
    }
}