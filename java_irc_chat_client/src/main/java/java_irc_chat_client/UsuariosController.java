package java_irc_chat_client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class UsuariosController {

    // ---------------- USUARIOS ----------------
    private static final String FILE_PATH_USUARIOS = System.getProperty("user.home") + "/.jirchat/usuarios_conocidos.xml";
    @FXML private TableView<UsuarioItem> tablaUsuarios;
    @FXML private TableColumn<UsuarioItem, String> colUsuario;
    @FXML private TableColumn<UsuarioItem, String> colComentario;
    @FXML private TableColumn<UsuarioItem, String> colSonido;
    @FXML private TextField txtUsuario;
    @FXML private TextField txtComentario;
    @FXML private TextField txtSonido;
    private final ObservableList<UsuarioItem> usuarios = FXCollections.observableArrayList();

    // ---------------- IGNORADOS ----------------
    private static final String FILE_PATH_IGNORADOS = System.getProperty("user.home") + "/.jirchat/ignorados.xml";
    @FXML private TableView<Ignorado> tablaIgnorados;
    @FXML private TableColumn<Ignorado, String> colNick;
    @FXML private TableColumn<Ignorado, String> colUserHost;
    @FXML private TableColumn<Ignorado, String> colFlags;
    @FXML private TextField txtIgnorarUsuario;
    @FXML private CheckBox chkPrivado;
    @FXML private CheckBox chkCanal;
    @FXML private CheckBox chkNotices;
    @FXML private CheckBox chkCTCP;
    @FXML private CheckBox chkDCC;
    @FXML private CheckBox chkInvite;
    @FXML private CheckBox chkCodes;
    
    @FXML private TabPane tabPanePrincipal;
    @FXML private Tab tabIgnorados; // asignar fx:id="tabIgnorados" al Tab de ignorados
    
    private final ObservableList<Ignorado> ignorados = FXCollections.observableArrayList();

    private ChatController chatController;
    public void setChatController(ChatController chatController) {
        this.chatController = chatController;    
    }
    
    
    
    @FXML
    public void initialize() {
        // Inicializar tabla usuarios
        colUsuario.setCellValueFactory(new PropertyValueFactory<>("usuario"));
        colComentario.setCellValueFactory(new PropertyValueFactory<>("comentario"));
        colSonido.setCellValueFactory(new PropertyValueFactory<>("sonido"));
        tablaUsuarios.setItems(usuarios);
        cargarUsuariosDesdeXML();

        // Inicializar tabla ignorados
        colNick.setCellValueFactory(new PropertyValueFactory<>("nick"));
        colUserHost.setCellValueFactory(new PropertyValueFactory<>("userHost"));
        colFlags.setCellValueFactory(new PropertyValueFactory<>("flags"));
        tablaIgnorados.setItems(ignorados);
        cargarIgnoradosDesdeXML();

        // Forzar refresco si la pestaña no está activa al iniciar
        Platform.runLater(() -> {
            tablaIgnorados.refresh();
        });
        
        tabPanePrincipal.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == tabIgnorados) {
                cargarIgnoradosDesdeXML();
            }
        });
    }


    // ---------------- MÉTODOS USUARIOS ----------------
 // Dentro de UsuariosController.java
    @FXML
    private void agregarUsuario() {
        String nick = txtUsuario.getText().trim();
        String comentario = txtComentario.getText().trim();
        String sonido = txtSonido.getText().trim();
        if (!nick.isEmpty()) {
            usuarios.add(new UsuarioItem(nick, comentario, sonido));
            guardarUsuariosEnXML();

            // ⭐ NOTIFICAR AL CHATCONTROLLER PARA ACTUALIZAR EL LISTVIEW
            if (chatController != null) {
            //    chatController.addNewKnownUser(nick);
            }

            txtUsuario.clear();
            txtComentario.clear();
            txtSonido.clear();
        }
    }

 // Dentro de UsuariosController.java

    @FXML
    private void eliminarUsuario() {
        UsuarioItem selected = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String nickEliminado = selected.getUsuario(); // Obtenemos el nick antes de eliminar
            
            usuarios.remove(selected);
            guardarUsuariosEnXML();
            
            // ⭐ NOTIFICACIÓN AL CHATCONTROLLER
            // Llama al método que crearemos en ChatController para eliminar de la lista lateral
            if (chatController != null) {
                chatController.removeKnownUser(nickEliminado);
            }
        }
    }

    @FXML
    private void guardarUsuariosEnXML() {
        try {
            File file = new File(FILE_PATH_USUARIOS);
            file.getParentFile().mkdirs();

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element root = doc.createElement("usuarios");
            doc.appendChild(root);

            for (UsuarioItem u : usuarios) {
                Element userEl = doc.createElement("usuario");
                userEl.setAttribute("nick", u.getUsuario());
                userEl.setAttribute("comentario", u.getComentario());
                userEl.setAttribute("sonido", u.getSonido());
                root.appendChild(userEl);
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(file));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cargarUsuariosDesdeXML() {
        try {
            File file = new File(FILE_PATH_USUARIOS);
            if (!file.exists()) return;

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("usuario");
            List<UsuarioItem> loaded = new ArrayList<>();

            for (int i = 0; i < nList.getLength(); i++) {
                Element e = (Element) nList.item(i);
                loaded.add(new UsuarioItem(
                        e.getAttribute("nick"),
                        e.getAttribute("comentario"),
                        e.getAttribute("sonido")
                ));
            }

            usuarios.setAll(loaded);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void limpiarTablaUsuarios() {
        usuarios.clear();
    }

    // ---------------- MÉTODOS IGNORADOS ----------------
    @FXML
    private void agregarIgnorado() {
        String nick = txtIgnorarUsuario.getText().trim();
        if (!nick.isEmpty()) {
            ignorados.add(new Ignorado(nick, "", obtenerFlags()));
            txtIgnorarUsuario.clear();
            chkPrivado.setSelected(false);
            chkCanal.setSelected(false);
            chkNotices.setSelected(false);
            chkCTCP.setSelected(false);
            chkDCC.setSelected(false);
            chkInvite.setSelected(false);
            chkCodes.setSelected(false);
            guardarIgnoradosEnXML();
        }
    }

    @FXML
    private void eliminarIgnorado() {
        Ignorado selected = tablaIgnorados.getSelectionModel().getSelectedItem();
        if (selected != null) {
            ignorados.remove(selected);
            guardarIgnoradosEnXML();
        }
    }

    @FXML
    private void borrarTodosIgnorados() {
        ignorados.clear();
        guardarIgnoradosEnXML();
    }

    @FXML
    private void actualizarIgnorados() {
        // Podrías recargar desde XML o refrescar la tabla
        cargarIgnoradosDesdeXML();
    }

    private String obtenerFlags() {
        StringBuilder flags = new StringBuilder();
        if (chkPrivado.isSelected()) flags.append("P");
        if (chkCanal.isSelected()) flags.append("C");
        if (chkNotices.isSelected()) flags.append("N");
        if (chkCTCP.isSelected()) flags.append("T");
        if (chkDCC.isSelected()) flags.append("D");
        if (chkInvite.isSelected()) flags.append("I");
        if (chkCodes.isSelected()) flags.append("O");
        return flags.toString();
    }

    private void guardarIgnoradosEnXML() {
        try {
            File file = new File(FILE_PATH_IGNORADOS);
            file.getParentFile().mkdirs();

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element root = doc.createElement("ignorados");
            doc.appendChild(root);

            for (Ignorado i : ignorados) {
                Element ignEl = doc.createElement("ignorado");
                ignEl.setAttribute("nick", i.getNick());
                ignEl.setAttribute("userHost", i.getUserHost());
                ignEl.setAttribute("flags", i.getFlags());
                root.appendChild(ignEl);

                // Log de escritura
                System.out.println("[GUARDAR] Ignorado -> nick: " + i.getNick() 
                    + ", userHost: " + i.getUserHost() + ", flags: " + i.getFlags());
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(file));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void cargarIgnoradosDesdeXML() {
        try {
            File file = new File(FILE_PATH_IGNORADOS);
            if (!file.exists()) {
                // Si no existe, cargar desde resources
                InputStream is = getClass().getResourceAsStream("/java_irc_chat_client/ignorados.xml");
                if (is == null) {
                    System.out.println("[CARGAR] No se encontró el recurso ignorados.xml");
                    return;
                }
                System.out.println("[CARGAR] Cargando ignorados desde resources");
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(is);
                doc.getDocumentElement().normalize();
                cargarIgnoradosDesdeDocumento(doc);
            } else {
                System.out.println("[CARGAR] Cargando ignorados desde archivo del usuario: " + FILE_PATH_IGNORADOS);
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(file);
                doc.getDocumentElement().normalize();
                cargarIgnoradosDesdeDocumento(doc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cargarIgnoradosDesdeDocumento(Document doc) {
        try {
            NodeList nList = doc.getElementsByTagName("ignorado");
            List<Ignorado> loaded = new ArrayList<>();

            for (int i = 0; i < nList.getLength(); i++) {
                Element e = (Element) nList.item(i);
                Ignorado ign = new Ignorado(
                    e.getAttribute("nick"),
                    e.getAttribute("userHost"),
                    e.getAttribute("flags")
                );
                loaded.add(ign);
                System.out.println("[CARGAR] Ignorado -> nick: " + ign.getNick() 
                    + ", userHost: " + ign.getUserHost() + ", flags: " + ign.getFlags());
            }

            ignorados.setAll(loaded);
            tablaIgnorados.refresh();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    // ---------------- CLASES INTERNAS ----------------
    public static class UsuarioItem {
        private final String usuario;
        private final String comentario;
        private final String sonido;

        public UsuarioItem(String usuario, String comentario, String sonido) {
            this.usuario = usuario;
            this.comentario = comentario;
            this.sonido = sonido;
        }

        public String getUsuario() { return usuario; }
        public String getComentario() { return comentario; }
        public String getSonido() { return sonido; }
    }

    public static class Ignorado {
        private final String nick;
        private final String userHost;
        private final String flags;

        public Ignorado(String nick, String userHost, String flags) {
            this.nick = nick;
            this.userHost = userHost;
            this.flags = flags;
        }

        public String getNick() { return nick; }
        public String getUserHost() { return userHost; }
        public String getFlags() { return flags; }
    }
    
    @FXML
    public void limpiarTabla() {
        usuarios.clear();
        ignorados.clear();
    }

}




