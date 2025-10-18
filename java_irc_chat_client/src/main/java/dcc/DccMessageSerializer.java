package dcc;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DccMessageSerializer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String toJson(DccMessage message) throws Exception {
        return mapper.writeValueAsString(message);
    }

    public static DccMessage fromJson(String json) throws Exception {
        return mapper.readValue(json, DccMessage.class);
    }
}