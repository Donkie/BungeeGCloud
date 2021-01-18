package donkie.bungeegcloud;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.protocol.packet.KeepAlive;

public class Events implements Listener {
    private BungeeGCloud plugin;
    private Random random = new Random();

    public Events(BungeeGCloud p) {
        this.plugin = p;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        this.plugin.getLogger().info("BungeeGClud onPostLogin " + player.getName());
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
        logger.info("BungeeGCloud onServerConnect " + event.getTarget().getSocketAddress() + " : "
                + event.getTarget().getName());

        if (!event.getTarget().getName().equals("lobby")) {
            return;
        }

        // Cancel the connect request, but don't disconnect the user.
        // This puts the user in an invalid state and throws an exception, which maybe
        // isn't very clean but it's necessary
        // for them to be put in the loading screen indefinitely until we're ready to go
        event.setCancelled(true);

        // Keep sending keep alive packets so the user doesn't time out
        ScheduledTask keepAliveTask = this.plugin.getProxy().getScheduler().schedule(this.plugin,
                () -> event.getPlayer().unsafe().sendPacket(new KeepAlive(random.nextLong())), 1, 5, TimeUnit.SECONDS);

        plugin.startServer((servinfo, error) -> {
            keepAliveTask.cancel();

            boolean playerSent = false;
            if (error == null && servinfo != null) {
                if (event.getPlayer().isConnected()) {
                    logger.info("Sending player");
                    event.getPlayer().connect(servinfo);
                }
                playerSent = true;
            } else {
                logger.warning(String.format("Failed to start the instance: %s", error.toString()));
            }

            if (!playerSent) {
                event.getPlayer().disconnect(TextComponent.fromLegacyText("Failed to start server"));
            }
        });
    }
}
