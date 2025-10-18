package java_irc_chat_client;

import javafx.beans.property.*;

public class Canal {

    private final StringProperty nombre = new SimpleStringProperty();
    private final IntegerProperty numUsuarios = new SimpleIntegerProperty();
    
    // ⭐ CAMBIO CLAVE: Propiedad para los modos/permisos
    private final StringProperty modos = new SimpleStringProperty(); 
    
    private final StringProperty descripcion = new SimpleStringProperty();

    public Canal(String nombre, int numUsuarios, String modos, String descripcion) {
        this.nombre.set(nombre);
        this.numUsuarios.set(numUsuarios);
        this.modos.set(modos); // ⭐ Ahora inicializa 'modos'
        this.descripcion.set(descripcion);
    }

    // --- Nombre ---
    public String getNombre() { return nombre.get(); }
    public void setNombre(String nombre) { this.nombre.set(nombre); }
    public StringProperty nombreProperty() { return nombre; }

    // --- Número de Usuarios ---
    public int getNumUsuarios() { return numUsuarios.get(); }
    public void setNumUsuarios(int num) { this.numUsuarios.set(num); }
    public IntegerProperty numUsuariosProperty() { return numUsuarios; }

    // --- Modos/Permisos (NUEVO MÉTODO) ---
    public String getModos() { return modos.get(); }
    public void setModos(String modos) { this.modos.set(modos); }
    
    // ⭐ EL MÉTODO REQUERIDO: modosProperty()
    public StringProperty modosProperty() { return modos; } 

    // --- Descripción ---
    public String getDescripcion() { return descripcion.get(); }
    public void setDescripcion(String descripcion) { this.descripcion.set(descripcion); }
    public StringProperty descripcionProperty() { return descripcion; }
}
