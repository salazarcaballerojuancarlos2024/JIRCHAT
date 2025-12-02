package java_irc_chat_client;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatLogger {

    private static final Logger log = LoggerFactory.getLogger(ChatLogger.class);

    private static final String LOGS_DIR_NAME = "logs";
    private static final File LOGS_DIR = new File(System.getProperty("user.dir") + File.separator + LOGS_DIR_NAME);
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // --- CONFIGURACIÓN DE LECTURA INVERSA ---
    private static final int BUFFER_SIZE = 2048; // Búfer para lectura inversa (2KB)

    // ==========================================================
    // UTILIDADES DE ARCHIVO
    // ==========================================================

    /**
     * Obtiene el Writer para el log. Crea el directorio y el archivo si no existen.
     * @param name Nombre del canal o usuario (sin extensión).
     * @return BufferedWriter para el archivo de log.
     * @throws IOException Si ocurre un error de I/O.
     */
    private static BufferedWriter getWriter(String name) throws IOException {
        if (!LOGS_DIR.exists()) {
            if (LOGS_DIR.mkdirs()) {
                log.info("Directorio de logs creado: {}", LOGS_DIR_NAME);
            } else {
                log.error("¡ERROR! No se pudo crear el directorio de logs.");
            }
        }
        File logFile = getLogFile(name);
        // 'true' en FileWriter indica modo 'append'
        return new BufferedWriter(new FileWriter(logFile, true));
    }
    
    /**
     * Obtiene el objeto File para un log específico.
     * @param logFileName El nombre del archivo (ej: "canal_general" o "nickremoto").
     * @return El objeto File.
     */
    private static File getLogFile(String logFileName) {
        // Tu lógica original usa el Path completo; simplificado aquí para usar LOGS_DIR.
        // Aseguramos que el nombre del log sea en minúsculas para consistencia en la clave.
        return new File(LOGS_DIR, logFileName.toLowerCase() + ".log"); 
    }

    // ==========================================================
    // 1. FUNCIONALIDADES DE ESCRITURA (EXISTENTES)
    // ==========================================================

    /**
     * Registra un mensaje de chat (usuario) en el log correspondiente.
     * @param name Nombre del canal o usuario de destino.
     * @param usuario Nick del usuario que envía el mensaje.
     * @param mensaje Contenido del mensaje.
     */
    public static void log(String name, String usuario, String mensaje) {
        try (BufferedWriter writer = getWriter(name)) {
            String timestamp = LocalDateTime.now().format(TS_FORMAT);
            // Formato: [yyyy-MM-dd HH:mm:ss] <Usuario> Mensaje
            writer.write("[" + timestamp + "] <" + usuario + "> " + mensaje);
            writer.newLine();
        } catch (IOException e) {
            log.error("Error I/O al registrar mensaje en log {}: {}", name, e.getMessage());
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
            // Formato: [yyyy-MM-dd HH:mm:ss] * Mensaje
            writer.write("[" + timestamp + "] * " + mensaje);
            writer.newLine();
        } catch (IOException e) {
            log.error("Error I/O al registrar mensaje de sistema en log {}: {}", name, e.getMessage());
        }
    }
    
    // ==========================================================
    // 2. FUNCIONALIDAD DE LECTURA DE HISTORIAL (NUEVAS Y MODIFICADAS)
    // ==========================================================

    /**
     * 📥 CARGA MODIFICADA: Lee las últimas 'count' líneas de un log específico (Eficiente).
     * Esta funcionalidad sustituye a 'cargarHistorial' en contextos donde se requiere un historial corto.
     * @param logFileName El nombre del archivo de log (ej: "nickremoto").
     * @param count El número máximo de líneas a leer desde el final.
     * @return Una lista de Strings que contienen las últimas líneas del log, en orden cronológico.
     */
    public static List<String> readLastLines(String logFileName, int count) {
        if (count <= 0) return Collections.emptyList();

        File logFile = getLogFile(logFileName);
        if (!logFile.exists() || logFile.length() == 0) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long position = raf.length();
            
            StringBuilder currentLine = new StringBuilder();

            while (position > 0 && lines.size() < count) {
                // Determinar el tamaño del bloque a leer
                long readSize = Math.min(BUFFER_SIZE, position);
                position -= readSize;

                // Mover el puntero al inicio del bloque y leer
                raf.seek(position);
                byte[] buffer = new byte[(int) readSize];
                raf.readFully(buffer, 0, (int) readSize);
                
                // Procesar el búfer de atrás hacia adelante
                for (int i = (int) readSize - 1; i >= 0; i--) {
                    char c = (char) buffer[i]; 

                    if (c == '\n') {
                        // Nueva línea: Añadir al inicio de la lista y reiniciar
                        if (currentLine.length() > 0) {
                            lines.add(0, currentLine.reverse().toString().trim()); 
                            currentLine = new StringBuilder(); 
                            
                            if (lines.size() >= count) return lines; 
                        }
                    } else if (c != '\r') {
                        currentLine.append(c);
                    }
                }
            }
            
            // Manejar la última línea incompleta/inicial si la hay
            if (currentLine.length() > 0) {
                lines.add(0, currentLine.reverse().toString().trim());
            }

        } catch (IOException e) {
            log.error("Error al leer las últimas líneas del log {}: {}", logFileName, e.getMessage());
            return lines.isEmpty() ? Collections.emptyList() : lines;
        }
        return lines;
    }

    /**
     * Carga el historial de mensajes COMPLETO de un canal o chat privado (Mantiene la funcionalidad original).
     * @param name Nombre del canal o usuario (sin extensión).
     * @return Una lista de cadenas, donde cada cadena es una línea del log.
     */
    public static List<String> cargarHistorial(String name) {
        Path logPath = getLogFile(name).toPath();
        
        if (!Files.exists(logPath)) {
            return new ArrayList<>();
        }

        try {
            // Leer todas las líneas del archivo (puede ser lento con logs muy grandes).
            return Files.lines(logPath).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error al cargar el historial COMPLETO para {}: {}", name, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ==========================================================
    // 3. FUNCIONALIDADES DE BÚSQUEDA Y LISTADO (EXISTENTES)
    // ==========================================================

    /** Lista todos los logs disponibles */
    public static List<File> listarLogs() {
        if (!LOGS_DIR.exists()) return Collections.emptyList();
        
        File[] files = LOGS_DIR.listFiles((d, name) -> name.endsWith(".log"));
        List<File> lista = new ArrayList<>();
        if (files != null) {
            Collections.addAll(lista, files);
        }
        return lista;
    }

    /** Busca logs cuyo nombre contenga la cadena o cuyo contenido la contenga */
    public static List<File> buscarLogs(String query) {
        if (query == null || query.isEmpty()) return listarLogs();
        
        List<File> resultados = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (File logFile : listarLogs()) {
            if (logFile.getName().toLowerCase().contains(lowerQuery) ||
                contenidoContiene(logFile, lowerQuery)) {
                resultados.add(logFile);
            }
        }
        return resultados;
    }

    /** Comprueba si un fichero contiene la cadena */
    public static boolean contenidoContiene(File file, String lowerQuery) {
        if (lowerQuery == null || lowerQuery.isEmpty()) return true;
        try {
            return Files.lines(file.toPath())
                        .anyMatch(line -> line.toLowerCase().contains(lowerQuery));
        } catch (IOException e) {
            log.error("Error I/O al buscar contenido en log {}: {}", file.getName(), e.getMessage());
            return false;
        }
    }
}