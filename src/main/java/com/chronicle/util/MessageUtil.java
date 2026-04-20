package com.chronicle.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;


public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() { }


    public static Component format(String template, Map<String, String> placeholders) {
        String resolved = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LEGACY.deserialize(resolved);
    }


    public static Component colorize(String template) {
        return LEGACY.deserialize(template);
    }

 
    public static String strip(String template) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(template)
                .toString();   
    }


    public static Component format(String template, String key, String value) {
        return format(template, Map.of(key, value));
    }


    public static Component prefixed(String prefix, Component message) {
        return colorize(prefix).append(message);
    }
}
