package donkie.bungeegcloud;

import java.io.IOException;
import java.util.logging.Logger;

import net.md_5.bungee.api.Callback;
import query.MCQuery;

public class StartInstanceRunnable implements Runnable {
    private BungeeGCloud plugin;
    private Callback<IPPort> callback;

    private static final long SERVER_STARTUP_TIMEOUT = 5 * 60 * 1000L;

    public StartInstanceRunnable(BungeeGCloud plugin, Callback<IPPort> callback) {
        this.plugin = plugin;
        this.callback = callback;
    }

    public void run() {
        IPPort ipport = null;
        Throwable error = null;

        try {
            Logger logger = plugin.getLogger();

            plugin.getMachine().updateRunningStatus();
            if (!plugin.getMachine().isRunning()) {
                logger.info("Starting instance");
                plugin.getMachine().start();
                logger.info("Instance started");
            } else {
                logger.info("Instance is running");
            }

            ipport = new IPPort(plugin.getMachine().getIp(), plugin.getServerPort());

            logger.info("Waiting for Minecraft start");
            StartInstanceRunnable.blockUntilServerUp(ipport, SERVER_STARTUP_TIMEOUT);
        } catch (Exception e) {
            error = e;
        }

        callback.done(ipport, error);
    }

    private static boolean isServerUp(IPPort ipport) {
        MCQuery query = new MCQuery(ipport.getIp(), ipport.getPort());
        try {
            query.basicStat();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void blockUntilServerUp(IPPort ipport, long timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!StartInstanceRunnable.isServerUp(ipport)) {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for Minecraft server to start");
            }

            Thread.sleep(2000);
        }
    }
}
