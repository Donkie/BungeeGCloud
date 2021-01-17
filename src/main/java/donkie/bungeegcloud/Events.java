package donkie.bungeegcloud;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.protocol.packet.KeepAlive;
import query.MCQuery;

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
    public void onServerConnect(ServerConnectEvent event) throws InterruptedException {
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

        StartInstanceRunnable runner = new StartInstanceRunnable(plugin, new Callback<IPPort>() {
			@Override
			public void done(IPPort ipport, Throwable error) {
			    keepAliveTask.cancel();

			    // TODO: Add check to make sure player is still online

			    boolean playerSent = false;
			    if(error == null){
			        logger.info("Sending player");
			        ServerInfo servinfo = plugin.getProxy().constructServerInfo("main", ipport.toAddress(), "Test", false);
			        event.getPlayer().connect(servinfo);
			        playerSent = true;
			    } else {
                    logger.warning(String.format("Failed to start the instance: %s", error.toString()));
                }

			    if (!playerSent) {
			        event.getPlayer().disconnect(TextComponent.fromLegacyText("Failed to start server"));
			    }
			}
		});
        this.plugin.getProxy().getScheduler().runAsync(this.plugin, runner);
    }
}
