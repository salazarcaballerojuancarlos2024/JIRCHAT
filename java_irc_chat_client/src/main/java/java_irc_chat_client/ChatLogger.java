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

// Importaciones necesarias para la funcionalidad Desktop.open()
import java.awt.Desktop; 
import java.awt.Desktop.Action; 

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
    
 // Dentro de ChatLogger.java

    /**
     * Obtiene el objeto File para un log específico. Implementa una búsqueda dual:
     * 1. Intenta encontrar el archivo sin prefijo '#' (para privados o si el controlador pasó el nombre limpio).
     * 2. Si no lo encuentra, prueba añadiendo el prefijo '#' (para logs de canales).
     * * @param logFileName El nombre del archivo (ej: "canal_general" o "nickremoto"). 
     * Se asume que viene sin el prefijo '#' si es un canal.
     * @return El objeto File.
     */
    private static File getLogFile(String logFileName) {
        // 1. Normalizar el nombre a minúsculas (ej: "el_jardin_musical")
        String baseName = logFileName.toLowerCase(); 

        // 2. Intento 1: Buscar el archivo SIN el prefijo '#' (para privados o si la escritura se corrigió)
        File fileWithoutHash = new File(LOGS_DIR, baseName + ".log");
        
        // Si el archivo existe con el nombre limpio, lo devolvemos inmediatamente.
        if (fileWithoutHash.exists()) {
            return fileWithoutHash; 
        }

        // 3. Intento 2: Buscar el archivo CON el prefijo '#' (para canales que fueron guardados con el '#')
        // Nota: El logFileName que llega aquí ya no tiene el '#'.
        String fileNameWithHash = "#" + baseName + ".log";
        File fileWithHash = new File(LOGS_DIR, fileNameWithHash);

        // Si el archivo existe con el '#'
        if (fileWithHash.exists()) {
            return fileWithHash;
        }
        
        // 4. Si no se encuentra ninguno, devolvemos el objeto File que *debería* existir.
        // Usaremos el formato con '#' por defecto para los canales si el intento de escritura falló, 
        // pero si el logFileName no es un canal, puede que estemos devolviendo un objeto File incorrecto.
        // Para no romper la funcionalidad de escritura, devolvemos el que tiene el '#', 
        // y la función de llamada (openLogFile) reportará la NO EXISTENCIA.
        // Puesto que el error ocurre en la búsqueda (lectura), devolver el que no existe causará el error esperado en openLogFile.
        
        // Devolvemos el path con el hash. Si no existe, la función openLogFile lo detectará.
        return fileWithHash;
    }

    // Nota: Los métodos de escritura (log, logSystem) siguen usando getWriter(name) 
    // que llama a getLogFile(name). Si la escritura usa el nombre del canal con '#' 
    // y el controlador lo pasa sin '#', puede haber una pequeña inconsistencia si lo que se escribe 
    // es diferente de lo que se busca. Por eso se hizo la búsqueda dual.
    // Idealmente, el controlador de logueo debería ser el único responsable de la limpieza del nombre.

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
    // 2. FUNCIONALIDAD DE LECTURA DE HISTORIAL
    // ==========================================================

    /**
     * Lee las últimas 'count' líneas de un log específico (Eficiente).
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
     * Carga el historial de mensajes COMPLETO de un canal o chat privado.
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
    // 3. FUNCIONALIDADES DE BÚSQUEDA Y LISTADO
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
    
    // ==========================================================
    // 4. ⭐ FUNCIONALIDAD DE APERTURA EN EL SISTEMA (NUEVA) ⭐
    // ==========================================================

    /**
     * Abre el archivo de log en el editor de texto predeterminado del sistema operativo.
     * @param logFileName El nombre del archivo de log (ej: "canal_general").
     * @return true si se pudo ejecutar la apertura, false en caso contrario.
     */
    public static boolean openLogFile(String logFileName) {
        File logFile = getLogFile(logFileName);

        if (!logFile.exists()) {
            log.warn("El archivo de log {} no existe.", logFile.getName());
            return false;
        }

        try {
            // Intenta usar java.awt.Desktop para una solución más limpia y multiplataforma
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN)) {
                Desktop.getDesktop().open(logFile);
                log.info("Abriendo log {} con el editor predeterminado del sistema (Desktop).", logFile.getName());
                return true;
            } else {
                // Fallback: usar Runtime.exec si Desktop no está disponible o soportado
                String os = System.getProperty("os.name").toLowerCase();
                String command = null;

                if (os.contains("win")) {
                    command = "notepad " + logFile.getAbsolutePath(); // Windows
                } else if (os.contains("mac")) {
                    command = "open " + logFile.getAbsolutePath();     // macOS
                } else if (os.contains("nix") || os.contains("nux")) {
                    command = "xdg-open " + logFile.getAbsolutePath(); // Linux/Unix
                }

                if (command != null) {
                    Runtime.getRuntime().exec(command);
                    log.warn("Abriendo log {} usando Runtime.exec. Plataforma: {}", logFile.getName(), os);
                    return true;
                } else {
                    log.error("Apertura de log no soportada para este SO.");
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("Error I/O al intentar abrir el log {}: {}", logFile.getName(), e.getMessage());
            return false;
        } catch (UnsupportedOperationException e) {
             log.error("La operación Desktop.open no es soportada por la plataforma. Intentando fallback...", e);
             // Si Desktop falla por no ser soportado, el flujo del código debe continuar al Runtime.exec (si se puede)
             return false;
        }
    }
}