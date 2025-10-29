package irc;

import java_irc_chat_client.CanalController;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public  class CanalVentana {
    public Stage stage;
    public CanalController controller;
    public AnchorPane rootPane;

    // Constructor ORIGINAL (TRES argumentos)
    public CanalVentana(Stage s, CanalController c, AnchorPane p) { 
        stage = s; controller = c; rootPane = p; 
    }
    
    // ⭐ CONSTRUCTOR NUEVO (DOS argumentos - Soluciona el error)
    public CanalVentana(Stage s, CanalController c) { 
        this(s, c, null); // Llama al constructor de 3 argumentos con null para el AnchorPane
    }
}