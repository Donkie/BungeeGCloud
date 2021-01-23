package donkie.bungeegcloud;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.protocol.packet.KeepAlive;

/**
 * Holds BungeeCord event listeners
 */
public class Events implements Listener {

    /** The plugin */
    private final BungeeGCloud plugin;

    /** RNG */
    private final Random random = new Random();

    public Events(BungeeGCloud p) {
        this.plugin = p;
    }

    @EventHandler
    public void onPing(ProxyPingEvent event) {
        ServerStatus status = plugin.getServerStatus();
        ServerPing ping = event.getResponse();
        ping.setPlayers(status.getPlayersForPing());
        if (status.isOnline()) {
            ping.setDescriptionComponent(new TextComponent(TextComponent.fromLegacyText(status.getMOTD())));
        } else {
            ping.setDescriptionComponent(new TextComponent(TextComponent.fromLegacyText("(P) " + status.getMOTD())));
        }
        event.setResponse(ping);
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        Logger logger = this.plugin.getLogger();

        if (!event.getTarget().getName().equals("lobby")) {
            return;
        }

        if (plugin.isServerRunning()) {
            try {
                event.setTarget(plugin.getServerInfo());
                return;
            } catch (NotOnlineException e) {
            }
        }

        logger.info("Server not running. Expected IllegalStateException incoming!");

        // Cancel the connect request, but don't disconnect the user.
        // This puts the user in an invalid state and throws an exception, which maybe
        // isn't very clean but it's necessary
        // for them to be put in the loading screen indefinitely until we're ready to go
        event.setCancelled(true);

        // Keep sending keep alive packets so the user doesn't time out
        ScheduledTask keepAliveTask = keepAlive(event.getPlayer());

        // Start the server asynchronously
        plugin.startServer((servinfo, error) -> {
            keepAliveTask.cancel();

            boolean playerSent = false;
            if (error == null && servinfo != null) {
                if (event.getPlayer().isConnected()) {
                    logger.info(String.format("Sending %s to the server.", event.getPlayer().getName()));
                    event.getPlayer().connect(servinfo);
                }
                playerSent = true;
            } else {
                logger.log(Level.SEVERE, "Failed to start the instance", error);
            }

            if (!playerSent) {
                if (error == null) {
                    event.getPlayer().disconnect(TextComponent.fromLegacyText("Failed to start server"));
                } else {
                    event.getPlayer().disconnect(TextComponent.fromLegacyText(String.format("Failed to start server: %s", error.getClass().getCanonicalName())));
                }
            }
        });
    }

    /**
     * Starts a task that periodically sends keep-alive packets to the player to
     * prevent them from timing out.
     *
     * @param player The player to keep alive
     * @return The task created. Cancel this when you don't wish to send keep-alive
     *         packets anymore.
     */
    private ScheduledTask keepAlive(ProxiedPlayer player) {
        return this.plugin.getProxy().getScheduler().schedule(this.plugin,
                () -> player.unsafe().sendPacket(new KeepAlive(random.nextLong())), 1, 5, TimeUnit.SECONDS);
    }
}
