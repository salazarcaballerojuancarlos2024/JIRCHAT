package java_irc_chat_client;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Representa a un usuario conocido cuya presencia es monitoreada en la aplicación.
 * Las propiedades usan el patrón JavaFX Property para permitir el data binding
 * y la actualización automática de la interfaz de usuario (TableView).
 */
public class UsuarioConocido {

	// --- PROPIEDADES OBSERVABLES ---
	
    // Nickname del usuario (Propiedad para la columna de Nick en la tabla)
    private final StringProperty nick;
    
    // Estado de conexión (Propiedad booleana para la columna de estado/color)
    private final BooleanProperty conectado;
  

    /**
     * Constructor que inicializa el nick y establece el estado de conexión inicial a desconectado.
     * @param nick El nombre del usuario.
     */
    public UsuarioConocido(String nick) {
        // Inicializa la propiedad del Nick con el valor dado
        this.nick = new SimpleStringProperty(nick);
        // Inicializa la propiedad de conexión a 'false' por defecto
        this.conectado = new SimpleBooleanProperty(false);
    }

    // =========================================================
    //         Getters y Setters (Para lógica interna)
    // =========================================================
    
    /**
     * Obtiene el valor simple del Nick.
     * @return El Nickname.
     */
    public String getNick() {
        return nick.get();
    }

    /**
     * Obtiene el valor simple del estado de conexión.
     * @return True si está conectado, false si no.
     */
    public boolean isConectado() {
        return conectado.get();
    }

    /**
     * Establece el estado de conexión. 
     * ¡Llamar a este método desde updateConnectionStatus() notifica a la UI!
     * @param conectado El nuevo estado.
     */
    public void setConectado(boolean conectado) {
        this.conectado.set(conectado);
    }

    // =========================================================
    //         Property Methods (CRÍTICO para JavaFX TableView)
    // =========================================================
    
    /**
     * Devuelve la propiedad de String para el Nick. 
     * Usado por: nickColumn.setCellValueFactory(new PropertyValueFactory<>("nick"));
     */
    public StringProperty nickProperty() {
        return nick;
    }

    /**
     * Devuelve la propiedad de Boolean para el estado de conexión.
     * Usado por: statusColumn.setCellValueFactory(...) para el sombreado.
     * @return La propiedad booleana observable.
     */
    public BooleanProperty conectadoProperty() {
        return conectado;
    }
}
