package donkie.bungeegcloud;

import java.io.IOException;
import java.util.logging.Logger;

import net.md_5.bungee.api.Callback;
import query.MCQuery;

public class StartInstanceRunnable implements Runnable {
    private BungeeGCloud plugin;
    private Callback<IPPort> callback;

    public StartInstanceRunnable(BungeeGCloud plugin, Callback<IPPort> callback) {
        this.plugin = plugin;
        this.callback = callback;
    }

    public void run() {
        IPPort ipport = null;
        Throwable error = null;

        try {
            Logger logger = plugin.getLogger();
            if (!plugin.isInstanceRunning()) {
                logger.info("Starting instance");
                plugin.startInstance();
                logger.info("Instance started");
            }

            ipport = new IPPort(plugin.getInstanceIP(), plugin.getServerPort());
            logger.info(String.format("Minecraft IP/Port: %s:%d", ipport.getIp(), ipport.getPort()));

            logger.info("Waiting for Minecraft start");
            StartInstanceRunnable.blockUntilServerUp(ipport.getIp(), ipport.getPort());
        } catch (Exception e) {
            error = e;
        }

        callback.done(ipport, error);
    }

    private static boolean isServerUp(String ip, int port) {
        MCQuery query = new MCQuery(ip, port);
        try {
            query.basicStat();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void blockUntilServerUp(String ip, int port) throws InterruptedException {
        while (!StartInstanceRunnable.isServerUp(ip, port)) {
            Thread.sleep(2000);
        }
    }
}
