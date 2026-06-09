package dev.maghreb.parkour.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Utility for converting legacy ampersand color codes to Adventure Components
 * and sending them to CommandSenders.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    public static Component colorize(String message) {
        return SERIALIZER.deserialize(message);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    public static void sendPrefixed(CommandSender sender, String prefix, String message) {
        sender.sendMessage(colorize(prefix + message));
    }
}
