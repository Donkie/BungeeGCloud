package donkie.bungeegcloud;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.api.services.compute.model.Instance;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.protocol.packet.KeepAlive;

import query.MCQuery;
import query.QueryResponse;

public class Events implements Listener {
    private Plugin plugin;
    private Random random = new Random();

    private static final String PROJECT_ID = "exhale-290316";
    private static final String ZONE_NAME = "europe-west2-c";
    private static final String INSTANCE_NAME = "minecraft-1";

    public Events(Plugin p) {
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
        logger.info("BungeeGCloud onServerConnect " + event.getTarget().getSocketAddress() + " : " + event.getTarget().getName());

        if(!event.getTarget().getName().equals("lobby")){
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

        ComputeEngineWrapper compute = new ComputeEngineWrapper(logger, PROJECT_ID);

        Runnable runner = new Runnable() {
            public void run() {
                boolean playerSent = false;
                try {
                    Instance instance = compute.getInstance(ZONE_NAME, INSTANCE_NAME);
                    String status = instance.getStatus();
                    logger.info(String.format("Instance status is: %s", status));
                    if (status.equals("STOPPING") || status.equals("SUSPENDING") || status.equals("SUSPENDED")
                            || status.equals("TERMINATED")) {
                        logger.info("Starting instance");
                        compute.startInstance(ZONE_NAME, INSTANCE_NAME).run();
                        logger.info("Instance started");
                    }
                    instance = compute.getInstance(ZONE_NAME, INSTANCE_NAME);
                    String ip = ComputeEngineWrapper.getInstanceExternalIP(instance);
                    logger.info(String.format("IP: %s", ip));

                    logger.info("Waiting for Minecraft start");
                    blockUntilServerUp(ip, 25565);

                    InetSocketAddress ipport = new InetSocketAddress(ip, 25565);

                    logger.info("Sending player");
                    ServerInfo servinfo = plugin.getProxy().constructServerInfo("main", ipport, "Test", false);
                    event.getPlayer().connect(servinfo);
                    playerSent = true;
                } catch (IOException e) {
                    logger.warning(e.getMessage());
                } catch (InterruptedException e) {
                    logger.warning(e.getMessage());
                }

                if(!playerSent){
                    event.getPlayer().disconnect("Failed to start server");
                }
            }
        };
        this.plugin.getProxy().getScheduler().runAsync(this.plugin, runner);
    }

    private boolean isServerUp(String ip, int port) {
        MCQuery query = new MCQuery(ip, port);
        try{
            query.basicStat();
            return true;
        } catch(IOException e) {
            return false;
        }
    }

    private void blockUntilServerUp(String ip, int port) throws InterruptedException {
        while(!isServerUp(ip, port)) {
            Thread.sleep(2000);
        }
    }
}
