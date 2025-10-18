package irc;

import java_irc_chat_client.CanalController;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public  class CanalVentana {
    public Stage stage;
    public CanalController controller;
    public AnchorPane rootPane;
    public CanalVentana(Stage s, CanalController c, AnchorPane p) { stage = s; controller = c; rootPane = p; }
}