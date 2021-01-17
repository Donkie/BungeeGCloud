package donkie.bungeegcloud;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import com.google.api.services.compute.model.Instance;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import query.MCQuery;
import query.QueryResponse;

public class BungeeGCloud extends Plugin {
    private final class ServerQueryRunnable implements Runnable {
        private int getOnlinePlayers(IPPort ipport) throws IOException {
            MCQuery query = new MCQuery(ipport.getIp(), ipport.getPort());
            QueryResponse resp = query.basicStat();
            return resp.getOnlinePlayers();
        }

        private int getOnlinePlayers() throws NotOnlineException {
            IPPort ipport;
            try {
                Instance instance = getInstance();
                if (!isInstanceRunning(instance)) {
                    throw new NotOnlineException();
                }

                ipport = new IPPort(getInstanceIP(instance), getServerPort());
            } catch (IOException e) {
                getLogger().warning(e.toString());
                throw new NotOnlineException();
            }

            try {
                return getOnlinePlayers(ipport);
            } catch (IOException e) {
                throw new NotOnlineException();
            }
        }

        public void run() {
            try {
                int onlinePlayers = getOnlinePlayers();
                if (onlinePlayers == 0 && stopServerTask == null) {
                    getLogger().info(
                            String.format("No players online. Stopping server in %d seconds...", STOP_SERVER_DELAY));
                    startStopTask();
                } else if(onlinePlayers > 0 && stopServerTask != null) {
                    getLogger().info("Players now online, aborting stop.");
                    cancelStopTask();
                }
            } catch (NotOnlineException e) {
            }
        }
    }

    private ComputeEngineWrapper compute;

    private static final String PROJECT_ID = "exhale-290316";
    private static final String ZONE_NAME = "europe-west2-c";
    private static final String INSTANCE_NAME = "minecraft-1";
    private static final int SERVER_PORT = 25565;

    /**
     * How often we should check for if there are any players online
     */
    private static final int SERVER_PLAYERS_CHECK_PERIOD = 5;

    /**
     * How long we should wait until stopping the server after last player left
     */
    private static final long STOP_SERVER_DELAY = 30L;// 5 * 60L

    private ScheduledTask stopServerTask = null;

    @Override
    public void onEnable() {
        try {
            compute = new ComputeEngineWrapper(PROJECT_ID);

            getProxy().getScheduler().schedule(this, new ServerQueryRunnable(), 1, SERVER_PLAYERS_CHECK_PERIOD, TimeUnit.SECONDS);
            getProxy().getPluginManager().registerListener(this, new Events(this));
        } catch (GeneralSecurityException | IOException e) {
            getLogger().warning(e.toString());
        }
    }

    public Instance getInstance() throws IOException {
        return compute.getInstance(ZONE_NAME, INSTANCE_NAME);
    }

    public boolean isInstanceRunning() throws IOException {
        return isInstanceRunning(getInstance());
    }

    public boolean isInstanceRunning(Instance instance) {
        String status = instance.getStatus();
        return !(status.equals("STOPPING") || status.equals("SUSPENDING") || status.equals("SUSPENDED")
                || status.equals("TERMINATED"));
    }

    public void startInstance() throws IOException, InterruptedException {
        compute.startInstance(ZONE_NAME, INSTANCE_NAME);
    }

    public void stopInstance() throws IOException, InterruptedException {
        compute.stopInstance(ZONE_NAME, INSTANCE_NAME);
    }

    public String getInstanceIP() throws IOException {
        return getInstanceIP(getInstance());
    }

    public String getInstanceIP(Instance instance) throws IOException {
        return ComputeEngineWrapper.getInstanceExternalIP(instance);
    }

    public int getServerPort() {
        return SERVER_PORT;
    }

    public void startStopTask() {
        if (stopServerTask != null) {
            stopServerTask.cancel();
        }

        stopServerTask = getProxy().getScheduler().schedule(BungeeGCloud.this, () -> {
            getLogger().info("Stopping instance due to inactivity...");
            try {
                stopInstance();
                getLogger().info("Instance stopped");
            } catch (IOException | InterruptedException e) {
                getLogger().warning(e.toString());
            }
        }, STOP_SERVER_DELAY, TimeUnit.SECONDS);
    }

    public void cancelStopTask() {
        if (stopServerTask != null) {
            stopServerTask.cancel();
            stopServerTask = null;
        }
    }
}
