package dcc;

import java.util.Locale;

public class NickUtils {
    public static String normalizeNick(String nick) {
        return nick == null ? null : nick.toLowerCase(Locale.ROOT);
    }
}
