package dcc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

public class NetworkUtils {

    /**
     * Intenta obtener la dirección IP pública del sistema consultando un servicio externo.
     * @return InetAddress con la IP pública, o null si falla.
     */
    public static InetAddress getPublicIPAddress() {
        // Usamos un servicio simple y conocido para obtener la IP pública en texto plano.
        String ipServiceUrl = "http://checkip.amazonaws.com"; 
        
        try {
            URL url = new URL(ipServiceUrl);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()))) {
                String ip = in.readLine();
                if (ip != null && !ip.isEmpty()) {
                    System.out.println("✅ IP pública obtenida: " + ip);
                    // 1. Convertir el String IP a InetAddress.
                    return InetAddress.getByName(ip);
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("❌ ERROR: No se pudo convertir el String de IP a InetAddress. " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ ERROR: No se pudo obtener la IP pública del servicio. Asegúrate de tener conexión.");
            // Si falla, se recomienda intentar usar la IP local por defecto o null.
        }
        
        // 2. Si falla la obtención, retorna la IP local o null.
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            System.err.println("❌ Fallback fallido: No se pudo obtener ni siquiera la IP local.");
            return null; 
        }
    }
}