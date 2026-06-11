package dev.maghreb.parkour.events;

import dev.maghreb.parkour.managers.RunManager;
import dev.maghreb.parkour.models.ActiveRun;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final RunManager runManager;

    public PlayerQuitListener(RunManager runManager) {
        this.runManager = runManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (runManager.isRunning(player.getUniqueId())) {
            runManager.invalidateRun(player, ActiveRun.InvalidationReason.QUIT);
        }
    }
}
