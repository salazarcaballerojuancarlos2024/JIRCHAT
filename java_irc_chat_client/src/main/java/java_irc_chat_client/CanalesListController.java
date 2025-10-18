package java_irc_chat_client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import irc.ChatBot; 
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class CanalesListController {

    @FXML private TableView<Canal> canalesTable;
    @FXML private TableColumn<Canal, String> colCanal;
    @FXML private TableColumn<Canal, String> colPermisos;
    @FXML private TableColumn<Canal, Integer> colUsuarios;
    @FXML private TableColumn<Canal, String> colDescripcion;

    @FXML private TextField txtBusqueda;
    @FXML private Button btnBuscar;

    private ChatBot bot; 
    private final ObservableList<Canal> canales = FXCollections.observableArrayList();
    private ChatController chatController;
    
    // ⭐ CALLBACK: Recibe canales uno por uno desde el ChatBot
    private final Consumer<Canal> canalReceiver = canal -> {
        Platform.runLater(() -> {
            // ⭐ DEBUGGING: Esta línea DEBE SALIR EN CONSOLA si el bot envía el canal.
            System.out.println("📥 UI RECIBIENDO CANAL: " + canal.getNombre());
            
            // Usamos una flag para ver si se añade:
            boolean exists = canales.stream()
                .anyMatch(c -> c.getNombre().equalsIgnoreCase(canal.getNombre()));
            
            if (!exists) {
                 canales.add(canal); 
                 // ⭐ DEBUGGING:
                 System.out.println("✨ Añadido canal a la lista: " + canal.getNombre());
            }
        });
    };
    
    // ⭐ CALLBACK: Se ejecuta al finalizar el listado (323)
    // -------------------------------------------------------------
    // MODIFICADO: APLICA LA ORDENACIÓN ALFABÉTICA POR NOMBRE DE CANAL
    // -------------------------------------------------------------
    private final Runnable listEndCallback = () -> {
        Platform.runLater(() -> {
            
            // ⭐ LÓGICA DE ORDENACIÓN: Ordena alfabéticamente por el nombre del canal.
            // Usamos Canal::getNombre (que devuelve el String del nombre).
            canales.sort(Comparator.comparing(Canal::getNombre));
            
            // Después de ordenar, restablecemos el orden de clasificación de la TableView para que
            // se muestre la columna del nombre como la columna ordenada.
            canalesTable.getSortOrder().clear(); 
            colCanal.setSortType(TableColumn.SortType.ASCENDING);
            canalesTable.getSortOrder().add(colCanal);
            
            if (chatController != null) {
                chatController.appendSystemMessage("🔍 Listado de canales completado. (" + canales.size() + " canales). Ordenado alfabéticamente.");
            }
        });
    };

    public void setChatController(ChatController chatController) {
        this.chatController = chatController;
    }

    public void setBot(ChatBot bot) {
        this.bot = bot;
        if (this.bot != null) {
            cargaCanalesServidor();
        }
    }

    @FXML
    public void initialize() {
        // --- CONFIGURACIÓN DE COLUMNAS (Mapeo de Propiedades) ---
        
        // 1. Nombre del Canal
        colCanal.setCellValueFactory(data -> data.getValue().nombreProperty());
        
        // 2. Modos/Permisos
        colPermisos.setCellValueFactory(data -> data.getValue().modosProperty());
        
        // 3. Usuarios 
        colUsuarios.setCellValueFactory(data -> data.getValue().numUsuariosProperty().asObject());
        
        // 4. Descripción 
        colDescripcion.setCellValueFactory(data -> data.getValue().descripcionProperty());
        
        // ⭐ Configuración del CellFactory para la descripción
        colDescripcion.setCellFactory(col -> new TableCell<Canal, String>() {
            private final TextFlow textFlow = new TextFlow();
            {
                textFlow.setPrefHeight(24);
                textFlow.setMinHeight(24);
                textFlow.setMaxHeight(24);
                textFlow.setLineSpacing(0);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    textFlow.getChildren().clear();
                    // ⭐ CLAVE: Usar el método para parsear colores IRC
                    textFlow.getChildren().addAll(parseIRCText(item)); 
                    textFlow.prefWidthProperty().bind(colDescripcion.widthProperty().subtract(10));
                    setGraphic(textFlow);
                }
            }
        });
        
        canalesTable.setItems(canales); 

        // ----------------------------------------------------------------------
        // MODIFICADO: ELIMINAMOS LA ORDENACIÓN INICIAL POR USUARIOS.
        // La ordenación alfabética se aplicará DE MANERA EXPLICITA en listEndCallback.
        // Sin embargo, configuramos la columna de canal como la ordenación predeterminada
        // visible para el usuario.
        // ----------------------------------------------------------------------
        canalesTable.getSortOrder().clear(); 
        colCanal.setSortType(TableColumn.SortType.ASCENDING);
        canalesTable.getSortOrder().add(colCanal); 

        // --- COMPORTAMIENTO DE FILAS ---
        canalesTable.setRowFactory(tv -> {
            TableRow<Canal> row = new TableRow<>() {
                @Override
                protected void updateItem(Canal item, boolean empty) {
                    super.updateItem(item, empty);
                    setPrefHeight(24);
                    setMinHeight(24);
                    setMaxHeight(24);

                    if (empty || item == null) {
                        setStyle("");
                    } else {
                        // Estilos de búsqueda y pares/impares
                        String search = txtBusqueda.getText();
                        if (search != null && !search.isEmpty() &&
                                item.getNombre().toLowerCase().contains(search.toLowerCase())) {
                            setStyle("-fx-background-color: #39FF14;"); // verde fosforito (Búsqueda)
                        } else if (getIndex() % 2 == 0) {
                            setStyle("-fx-background-color: #FFF8DC;"); // vainilla (Par)
                        } else {
                            setStyle("-fx-background-color: #ADD8E6;"); // azul claro (Impar)
                        }
                    }
                }
            };

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Canal canal = row.getItem();
                    if (chatController != null && canal != null) {
                        Platform.runLater(() -> {
                            try {
                                chatController.abrirCanal(canal.getNombre());
                                // Cierre de ventana opcional
                                if (canalesTable.getScene().getWindow() instanceof Stage) {
                                    ((Stage) canalesTable.getScene().getWindow()).close(); 
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo abrir el canal: " + e.getMessage(), ButtonType.OK);
                                alert.setTitle("Error al abrir canal");
                                alert.showAndWait();
                            }
                        });
                    }
                }
            });
            return row;
        });

        canalesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        btnBuscar.setOnAction(e -> canalesTable.refresh());
    }

    // -----------------------------------------------------------
    // Método auxiliar para parseo de colores IRC (Mantenido)
    // -----------------------------------------------------------

    private List<Text> parseIRCText(String ircText) {
        List<Text> texts = new ArrayList<>();
        if (ircText == null || ircText.isEmpty()) return texts;

        // Colores IRC estándar mapeados a JavaFX Color
        Color[] ircColors = {
                Color.WHITE, Color.BLACK, Color.DODGERBLUE, Color.LIMEGREEN, Color.RED,
                Color.SADDLEBROWN, Color.MEDIUMPURPLE, Color.ORANGE, Color.GOLD,
                Color.GREEN, Color.CYAN, Color.TURQUOISE, Color.ROYALBLUE,
                Color.HOTPINK, Color.DARKGREY, Color.LIGHTGREY
        };

        Color currentColor = Color.BLACK;
        int i = 0;
        while (i < ircText.length()) {
            char c = ircText.charAt(i);
            if (c == '\u0003') { // Código de color (Control-C)
                i++;
                StringBuilder number = new StringBuilder();
                while (i < ircText.length() && Character.isDigit(ircText.charAt(i)) && number.length() < 2) {
                    number.append(ircText.charAt(i));
                    i++;
                }
                if (number.length() > 0) {
                    int code = Integer.parseInt(number.toString());
                    currentColor = code >= 0 && code < ircColors.length ? ircColors[code] : Color.BLACK;
                } else currentColor = Color.BLACK; // Si es \u0003 sin número, resetea a negro
            } else if (c == '\u000f') { // Código de reseteo (Control-O)
                currentColor = Color.BLACK;
                i++;
            } else {
                StringBuilder sb = new StringBuilder();
                while (i < ircText.length() && ircText.charAt(i) != '\u0003' && ircText.charAt(i) != '\u000f') {
                    sb.append(ircText.charAt(i));
                    i++;
                }
                Text t = new Text(sb.toString());
                t.setFill(currentColor);
                texts.add(t);
            }
        }
        return texts;
    }


    public void cargaCanalesServidor() {
        if (bot == null || !bot.isConnected()) {
            if (chatController != null) {
                chatController.appendSystemMessage("❌ Error: Conéctate al servidor antes de listar canales.");
            }
            return;
        }
        Platform.runLater(canales::clear);

        // ⭐ CLAVE: Este método llama a PircBot 1.5.0 listChannels()
        bot.requestChannelList(canalReceiver, listEndCallback);
    }

    public List<Canal> getCanalesSeleccionados() {
        return new ArrayList<>(canalesTable.getSelectionModel().getSelectedItems());
    }

    public void shutdown() {
        // No se requiere limpieza especial.
    }
}