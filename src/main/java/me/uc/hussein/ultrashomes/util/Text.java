package me.uc.hussein.ultrashomes.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** MiniMessage helpers shared by messages, GUI items and console output. */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern LEFTOVER = Pattern.compile("\\{[a-z0-9_\\-]+}");

    private Text() {
    }

    public static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    public static Component mm(String s) {
        return MM.deserialize(s == null ? "" : s);
    }

    /** Item name/lore component: never italic by default. */
    public static Component item(String s) {
        return mm(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Replaces {key} with the escaped value; keys ending in "-mm" are inserted as raw MiniMessage. */
    public static String fill(String tpl, Map<String, String> ph) {
        if (tpl == null) return "";
        if (ph == null || ph.isEmpty()) return tpl;
        String out = tpl;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            if (!e.getKey().endsWith("-mm")) {
                v = MM.escapeTags(v);
            }
            out = out.replace("{" + e.getKey() + "}", v);
        }
        return out;
    }

    public static String strip(String mm) {
        if (mm == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(mm(mm));
    }

    public static String num(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
