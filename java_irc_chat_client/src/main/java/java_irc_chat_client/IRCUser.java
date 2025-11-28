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
    
    // ⭐ PROPIEDAD CRÍTICA: Indica el estado de conexión para la UI (color verde/rojo)
    private final BooleanProperty isConnected = new SimpleBooleanProperty(false);


    // --- PROPIEDADES ADICIONALES PARA EL DETALLE (statusText) ---
    private final StringProperty serverInfo = new SimpleStringProperty();
    private final StringProperty connectTime = new SimpleStringProperty();
    private final StringProperty idleTime = new SimpleStringProperty();


    /**
     * Constructor usado por ChatBot.onServerResponse (WHO reply 352).
     */
    public IRCUser(String nick, String userHost, String flags, String server, String realName) {
        // Asignación de valores a las propiedades
        setNick(nick);
        setUserHost(userHost);
        setFlags(flags);
        setServer(server);
        setRealName(realName);
        
        // Inicializar propiedades de estado
        setIsConnected(true); // El 352 (WHO) siempre indica que el usuario está conectado.
        setServerInfo("N/A"); 
        setConnectTime("N/A"); 
        setIdleTime("0s"); 
    }
    
    // =========================================================
    //         Getters y Setters (Para lógica interna y texto)
    // =========================================================
    
    // --- Getters de Tabla ---
    public String getNick() { return nick.get(); }
    public String getUserHost() { return userHost.get(); }
    public String getFlags() { return flags.get(); }
    public String getServer() { return server.get(); }
    public String getRealName() { return realName.get(); }

    // --- Getters y Setters de Conexión (Método directo) ---
    public boolean getIsConnected() { return isConnected.get(); }
    public void setIsConnected(boolean value) { isConnected.set(value); } // Usado por updateConnectionStatus
    
    // --- Getters de Detalle (Para statusText) ---
    public String getServerInfo() { return serverInfo.get(); }
    public String getConnectTime() { return connectTime.get(); }
    public String getIdleTime() { return idleTime.get(); }
    
    // --- Setters (Usados internamente) ---
    public void setNick(String value) { nick.set(value); }
    public void setUserHost(String value) { userHost.set(value); }
    public void setFlags(String value) { flags.set(value); }
    public void setServer(String value) { server.set(value); }
    public void setRealName(String value) { realName.set(value); }
    public void setServerInfo(String value) { serverInfo.set(value); }
    public void setConnectTime(String value) { connectTime.set(value); }
    public void setIdleTime(String value) { idleTime.set(value); }


    // =========================================================
    //         Property Methods (CRÍTICO para JavaFX TableView)
    // =========================================================
    
    public StringProperty nickProperty() { return nick; }
    public StringProperty userHostProperty() { return userHost; }
    public StringProperty flagsProperty() { return flags; }
    public StringProperty serverProperty() { return server; }
    public StringProperty realNameProperty() { return realName; }
    
    // ⭐ PROPIEDAD CRÍTICA: Enlaza el estado booleano a la celda de la UI
    public BooleanProperty isConnectedProperty() { return isConnected; }
    
    // Propiedades adicionales
    public StringProperty serverInfoProperty() { return serverInfo; }
    public StringProperty connectTimeProperty() { return connectTime; }
    public StringProperty idleTimeProperty() { return idleTime; }

}