package java_irc_chat_client;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class IRCUser {

    // --- PROPIEDADES VISIBLES EN LA TABLA (Tabla usa Property()) ---
    private final StringProperty nick = new SimpleStringProperty();           // Columna 1
    private final StringProperty userHost = new SimpleStringProperty();       // Columna 2 (nick@host)
    private final StringProperty flags = new SimpleStringProperty();          // Columna 3
    private final StringProperty server = new SimpleStringProperty();         // Columna 4
    private final StringProperty realName = new SimpleStringProperty();       // Columna 5 (Mapea el campo 'username' de tu constructor)

    // --- PROPIEDADES ADICIONALES PARA EL DETALLE (statusText usa getXxx()) ---
    private final StringProperty serverInfo = new SimpleStringProperty();
    private final StringProperty connectTime = new SimpleStringProperty();
    private final StringProperty idleTime = new SimpleStringProperty();


    /**
     * Constructor usado por ChatBot.onServerResponse (WHO reply).
     * Los parámetros corresponden a: nick, nick@host, flags, server, username (el Ident/RealName en el FXML).
     */
    public IRCUser(String nick, String userHost, String flags, String server, String realName) {
        // Asignación de valores a las propiedades
        setNick(nick);
        setUserHost(userHost);
        setFlags(flags);
        setServer(server);
        setRealName(realName);
        
        // Inicializar campos adicionales (no vienen en la respuesta 352 estándar)
        setServerInfo("N/A"); 
        setConnectTime("N/A"); 
        setIdleTime("0s"); // Inactivo
    }
    
    // =========================================================
    //         Getters y Setters (Para lógica y statusText)
    // =========================================================
    
    // --- Getters de Tabla ---
    public String getNick() { return nick.get(); }
    public String getUserHost() { return userHost.get(); }
    public String getFlags() { return flags.get(); }
    public String getServer() { return server.get(); }
    public String getRealName() { return realName.get(); } // Usado para mostrar el Ident/Username
    
    // --- Getters de Detalle (Para statusText) ---
    public String getServerInfo() { return serverInfo.get(); }
    public String getConnectTime() { return connectTime.get(); }
    public String getIdleTime() { return idleTime.get(); }
    
    // Setters (Si son necesarios, aunque aquí solo los usamos internamente)
    public void setNick(String value) { nick.set(value); }
    public void setUserHost(String value) { userHost.set(value); }
    public void setFlags(String value) { flags.set(value); }
    public void setServer(String value) { server.set(value); }
    public void setRealName(String value) { realName.set(value); }
    public void setServerInfo(String value) { serverInfo.set(value); }
    public void setConnectTime(String value) { connectTime.set(value); }
    public void setIdleTime(String value) { idleTime.set(value); }


    // =========================================================
    //         Property Methods (CRÍTICO para TableView)
    // =========================================================
    
    public StringProperty nickProperty() { return nick; }
    public StringProperty userHostProperty() { return userHost; }
    public StringProperty flagsProperty() { return flags; }
    public StringProperty serverProperty() { return server; }
    public StringProperty realNameProperty() { return realName; }
    
    // Propiedades adicionales (si quisieras mostrarlas en la tabla)
    public StringProperty serverInfoProperty() { return serverInfo; }
    public StringProperty connectTimeProperty() { return connectTime; }
    public StringProperty idleTimeProperty() { return idleTime; }

}