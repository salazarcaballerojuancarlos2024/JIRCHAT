package dcc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class DccMessageReceiver {

    private int listeningPort;

    public DccMessageReceiver(int port) {
        this.listeningPort = port;
    }

    public void listen() {
        try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
            System.out.println("Esperando mensajes DCC en el puerto " + listeningPort);

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }

                    DccMessage receivedMessage = DccMessageSerializer.fromJson(sb.toString());
                    System.out.println("Mensaje recibido: " + receivedMessage);

                    // Aquí podrías implementar lógica adicional según el tipo de mensaje
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}