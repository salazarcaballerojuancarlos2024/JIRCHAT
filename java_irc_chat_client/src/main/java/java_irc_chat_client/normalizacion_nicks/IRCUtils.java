package java_irc_chat_client.normalizacion_nicks;


public class IRCUtils {

    /**
     * Normaliza un nick de IRC según las reglas comunes (minúsculas y caracteres especiales)
     */
    public static String normalizeNick(String nick) {
        if (nick == null) return null;
        return nick.toLowerCase()
                   .replace('[', '{')
                   .replace(']', '}')
                   .replace('\\', '|');
    }
}
