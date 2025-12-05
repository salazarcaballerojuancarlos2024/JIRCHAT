package java_irc_chat_client;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class IRCUser {

    // --- PROPIEDADES VISIBLES EN LA TABLA (Usan Property()) ---
    private final StringProperty nick = new SimpleStringProperty();
    private final StringProperty userHost = new SimpleStringProperty(); 
    private final StringProperty flags = new SimpleStringProperty();      
    private final StringProperty server = new SimpleStringProperty();     
    private final StringProperty realName = new SimpleStringProperty(); 
    
    // ⭐ PROPIEDADES AÑADIDAS PARA EL PARSING Y FUNCIONALIDAD
    private final StringProperty user = new SimpleStringProperty(); 
    private final StringProperty host = new SimpleStringProperty(); 
    private final StringProperty channel = new SimpleStringProperty();
    
    // ⭐ PROPIEDAD CRÍTICA: Indica el estado de conexión para la UI (color verde/rojo)
    private final BooleanProperty isConnected = new SimpleBooleanProperty(false);


    // --- PROPIEDADES ADICIONALES PARA EL DETALLE (statusText) ---
    private final StringProperty serverInfo = new SimpleStringProperty();
    private final StringProperty connectTime = new SimpleStringProperty();
    private final StringProperty idleTime = new SimpleStringProperty();


    // =========================================================
    //         CONSTRUCTORES
    // =========================================================

    /**
     * ⭐ CONSTRUCTOR POR DEFECTO AÑADIDO: Necesario para introspección de JavaFX.
     */
    public IRCUser() {
        this("N/A"); // Llama al constructor principal para inicializar todo.
    }
    
    /**
     * CONSTRUCTOR PRINCIPAL: Necesario para inicializar el objeto durante el parseo WHO (352).
     * @param nick El nick del usuario.
     */
    public IRCUser(String nick) {
        setNick(nick);
        // Inicializar propiedades de estado por defecto
        setIsConnected(true); 
        setUserHost("N/A"); 
        setFlags("");
        setServer("N/A");
        setRealName("N/A");
        setChannel(""); 
        setUser("");
        setHost("");
        setServerInfo("N/A"); 
        setConnectTime("N/A"); 
        setIdleTime("0s"); 
    }
    
    /**
     * Constructor detallado.
     */
    public IRCUser(String nick, String userHost, String flags, String server, String realName) {
        this(nick); 
        setUserHost(userHost);
        setFlags(flags);
        setServer(server);
        setRealName(realName);
    }
    
    // =========================================================
    //         Getters y Setters
    // =========================================================
    
    // --- Getters de Tabla/Detalle ---
    public String getNick() { return nick.get(); }
    public String getUserHost() { return userHost.get(); }
    public String getFlags() { return flags.get(); }
    public String getServer() { return server.get(); }
    public String getRealName() { return realName.get(); }
    public String getUser() { return user.get(); } 
    public String getHost() { return host.get(); } 
    public String getChannel() { return channel.get(); }
    
    // --- Getters y Setters de Conexión (Método directo) ---
    public boolean getIsConnected() { return isConnected.get(); }
    public void setIsConnected(boolean value) { isConnected.set(value); } 
    
    // --- Getters de Detalle (Para statusText) ---
    public String getServerInfo() { return serverInfo.get(); }
    public String getConnectTime() { return connectTime.get(); }
    public String getIdleTime() { return idleTime.get(); }
    
    // --- Setters (Usados internamente y por ChatBot.parseWhoResponse) ---
    public void setNick(String value) { nick.set(value); }
    public void setUserHost(String value) { userHost.set(value); }
    public void setFlags(String value) { flags.set(value); }
    public void setServer(String value) { server.set(value); }
    public void setRealName(String value) { realName.set(value); }
    public void setUser(String value) { user.set(value); } 
    public void setHost(String value) { host.set(value); } 
    public void setChannel(String value) { channel.set(value); } 
    public void setServerInfo(String value) { serverInfo.set(value); }
    public void setConnectTime(String value) { connectTime.set(value); }
    public void setIdleTime(String value) { idleTime.set(value); }


    // =========================================================
    //         Property Methods
    // =========================================================
    
    public StringProperty nickProperty() { return nick; }
    public StringProperty userHostProperty() { return userHost; }
    public StringProperty flagsProperty() { return flags; }
    public StringProperty serverProperty() { return server; }
    public StringProperty realNameProperty() { return realName; }
    public StringProperty channelProperty() { return channel; } 
    
    public BooleanProperty isConnectedProperty() { return isConnected; }
    
    // Propiedades adicionales
    public StringProperty userProperty() { return user; }
    public StringProperty hostProperty() { return host; }
    public StringProperty serverInfoProperty() { return serverInfo; }
    public StringProperty connectTimeProperty() { return connectTime; }
    public StringProperty idleTimeProperty() { return idleTime; }

}