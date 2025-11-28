package java_irc_chat_client;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatLogger {

    private static final String LOGS_DIR =
            System.getProperty("user.dir") + File.separator + "logs";
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Obtiene el BufferedWriter para el log. Crea el directorio y el archivo si no existen.
     * @param name Nombre del canal o usuario (sin extensión).
     * @return BufferedWriter para el archivo de log.
     * @throws IOException Si ocurre un error de I/O.
     */
    private static BufferedWriter getWriter(String name) throws IOException {
        File dir = new File(LOGS_DIR);
        if (!dir.exists()) dir.mkdirs();
        File logFile = new File(dir, name + ".log");
        // 'true' en FileWriter indica modo 'append' (añadir al final)
        return new BufferedWriter(new FileWriter(logFile, true));
    }

    /**
     * Registra un mensaje de chat (usuario) en el log correspondiente.
     * @param name Nombre del canal o usuario de destino.
     * @param usuario Nick del usuario que envía el mensaje.
     * @param mensaje Contenido del mensaje.
     */
    public static void log(String name, String usuario, String mensaje) {
        try (BufferedWriter writer = getWriter(name)) {
            String timestamp = LocalDateTime.now().format(TS_FORMAT);
            writer.write("[" + timestamp + "] <" + usuario + "> " + mensaje);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Registra un mensaje del sistema (ej. JOIN/PART/QUIT) en el log correspondiente.
     * @param name Nombre del canal o usuario de destino.
     * @param mensaje Contenido del mensaje del sistema.
     */
    public static void logSystem(String name, String mensaje) {
        try (BufferedWriter writer = getWriter(name)) {
            String timestamp = LocalDateTime.now().format(TS_FORMAT);
            writer.write("[" + timestamp + "] * " + mensaje);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // --- NUEVA FUNCIONALIDAD: CARGAR HISTORIAL ---

    /**
     * 📥 Carga el historial de mensajes de un canal o chat privado.
     * Si el archivo no existe, devuelve una lista vacía.
     * @param name Nombre del canal o usuario (sin extensión).
     * @return Una lista de cadenas, donde cada cadena es una línea del log.
     */
    public static List<String> cargarHistorial(String name) {
        Path logPath = Path.of(LOGS_DIR, name + ".log");
        
        if (!Files.exists(logPath)) {
            // Si el archivo no existe, no hay historial, se devuelve una lista vacía.
            // (El archivo se creará automáticamente cuando se escriba el primer log)
            return new ArrayList<>();
        }

        try {
            // Leer todas las líneas del archivo.
            return Files.lines(logPath).collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error al cargar el historial para " + name + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ---------------------------------------------
    
    /** Lista todos los logs disponibles */
    public static List<File> listarLogs() {
        // ... (Se mantiene el código existente) ...
        File dir = new File(LOGS_DIR);
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        List<File> lista = new ArrayList<>();
        if (files != null) {
            for (File f : files) lista.add(f);
        }
        return lista;
    }

    /** Busca logs cuyo nombre contenga la cadena o cuyo contenido la contenga */
    public static List<File> buscarLogs(String query) {
        // ... (Se mantiene el código existente) ...
        if (query == null || query.isEmpty()) return listarLogs();
        List<File> resultados = new ArrayList<>();
        for (File log : listarLogs()) {
            if (log.getName().toLowerCase().contains(query.toLowerCase()) ||
                contenidoContiene(log, query)) {
                resultados.add(log);
            }
        }
        return resultados;
    }

    /** Comprueba si un fichero contiene la cadena */
    public static boolean contenidoContiene(File file, String query) {
        // ... (Se mantiene el código existente) ...
        if (query == null || query.isEmpty()) return true;
        try {
            return Files.lines(file.toPath())
                        .anyMatch(line -> line.toLowerCase().contains(query.toLowerCase()));
        } catch (IOException e) {
            return false;
        }
    }
}