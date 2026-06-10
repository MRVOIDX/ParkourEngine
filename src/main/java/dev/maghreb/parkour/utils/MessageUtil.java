package dev.maghreb.parkour.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Centralised message utility using Bukkit ChatColor only.
 * Avoids any Adventure API conflict with the server's bundled version.
 */
public final class MessageUtil {

    private MessageUtil() {}

    /** Translates & colour codes into real colour codes. */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }
}
