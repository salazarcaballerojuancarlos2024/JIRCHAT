package dcc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DccMessage {

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("dcc_command")
    private String dccCommand;

    @JsonProperty("sender_nick")
    private String senderNick;

    @JsonProperty("receiver_nick")
    private String receiverNick;

    @JsonProperty("ip_origin")
    private String ipOrigin;

    @JsonProperty("ip_destination")
    private String ipDestination;

    @JsonProperty("port_origin")
    private int portOrigin;

    @JsonProperty("port_destination")
    private int portDestination;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("file_size_bytes")
    private long fileSizeBytes;

    public DccMessage() {}

    public DccMessage(String timestamp, String messageType, String dccCommand,
                      String senderNick, String receiverNick,
                      String ipOrigin, String ipDestination,
                      int portOrigin, int portDestination,
                      String filePath, long fileSizeBytes) {
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.dccCommand = dccCommand;
        this.senderNick = senderNick;
        this.receiverNick = receiverNick;
        this.ipOrigin = ipOrigin;
        this.ipDestination = ipDestination;
        this.portOrigin = portOrigin;
        this.portDestination = portDestination;
        this.filePath = filePath;
        this.fileSizeBytes = fileSizeBytes;
    }

    // Getters y Setters omitidos por brevedad, pero los necesitas todos
    // Puedes generarlos automáticamente en tu IDE

    @Override
    public String toString() {
        return "DccMessage{" +
                "timestamp='" + timestamp + '\'' +
                ", messageType='" + messageType + '\'' +
                ", dccCommand='" + dccCommand + '\'' +
                ", senderNick='" + senderNick + '\'' +
                ", receiverNick='" + receiverNick + '\'' +
                ", ipOrigin='" + ipOrigin + '\'' +
                ", ipDestination='" + ipDestination + '\'' +
                ", portOrigin=" + portOrigin +
                ", portDestination=" + portDestination +
                ", filePath='" + filePath + '\'' +
                ", fileSizeBytes=" + fileSizeBytes +
                '}';
    }
}