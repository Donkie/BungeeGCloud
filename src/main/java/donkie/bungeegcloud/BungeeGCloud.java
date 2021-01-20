package donkie.bungeegcloud;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import query.MCQuery;
import query.QueryResponse;

public class BungeeGCloud extends Plugin {
    private final class ServerQueryRunnable implements Runnable {
        private void updateServerStatus(IPPort ipport, ServerStatus status) {
            MCQuery query = new MCQuery(ipport.getIp(), ipport.getPort());
            QueryResponse resp;
            try {
                resp = query.basicStat();
                status.update(resp);
            } catch (IOException e) {
                status.setOffline();
            }
        }

        private void updateServerStatus(ServerStatus status) throws NotOnlineException {
            IPPort ipport;
            try {
                instance.updateRunningStatus();
                if (!instance.isRunning()) {
                    status.setOffline();
                    throw new NotOnlineException();
                }

                ipport = new IPPort(instance.getIp(), getServerPort());
            } catch (IOException e) {
                status.setOffline();
                getLogger().log(Level.SEVERE, "Failed to update the instance status", e);
                throw new NotOnlineException();
            }

            updateServerStatus(ipport, status);
        }

        public void run() {
            if (serverStarting) {
                return;
            }

            try {
                updateServerStatus(serverStatus);
            } catch (NotOnlineException e) {
                return;
            }

            int onlinePlayers = serverStatus.getOnlinePlayers();
            if (onlinePlayers == 0 && stopServerTask == null) {
                getLogger()
                        .info(String.format("No players online. Stopping server in %d seconds...", STOP_SERVER_DELAY));
                startStopTask();
            } else if (onlinePlayers > 0 && stopServerTask != null) {
                getLogger().info("Players now online, aborting stop.");
                cancelStopTask();
            }
        }
    }

    private ComputeEngineWrapper compute;

    private static final String PROJECT_ID = "exhale-290316";
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

    private ServerStatus serverStatus = new ServerStatus(20, "Welcome to Exhale!");

    private boolean serverStarting = false;

    private InstanceWrapper instance;

    @Override
    public void onEnable() {
        try {
            compute = new ComputeEngineWrapper(PROJECT_ID);

            instance = new InstanceWrapper(INSTANCE_NAME, compute, getLogger());
            instance.fetchZone();

            getProxy().getScheduler().schedule(this, new ServerQueryRunnable(), 1, SERVER_PLAYERS_CHECK_PERIOD,
                    TimeUnit.SECONDS);
            getProxy().getPluginManager().registerListener(this, new Events(this));
        } catch (GeneralSecurityException | IOException e) {
            getLogger().log(Level.SEVERE, "Failed to setup compute engine", e);
        }
    }

    public boolean isServerRunning() {
        try {
            MCQuery query = new MCQuery(instance.getIp(), SERVER_PORT);
            query.basicStat();
            return true;
        } catch (NotOnlineException | IOException e) {
            return false;
        }
    }

    public InstanceWrapper getInstance() {
        return instance;
    }

    public ServerInfo getServerInfo() throws NotOnlineException {
        return getServerInfo(new IPPort(instance.getIp(), SERVER_PORT));
    }

    public ServerInfo getServerInfo(IPPort ipport) {
        return getProxy().constructServerInfo("main", ipport.toAddress(), "Test", false);
    }

    public void startServer(Callback<ServerInfo> callback) {
        serverStarting = true;
        cancelStopTask();

        StartInstanceRunnable runner = new StartInstanceRunnable(this, (ipport, error) -> {
            serverStarting = false;
            ServerInfo serverinfo = null;
            if (ipport != null) {
                serverinfo = getServerInfo(ipport);
            }
            callback.done(serverinfo, error);
        });
        getProxy().getScheduler().runAsync(this, runner);
    }

    public int getServerPort() {
        return SERVER_PORT;
    }

    public void startStopTask() {
        if (stopServerTask != null) {
            stopServerTask.cancel();
        }

        if (serverStarting) {
            return;
        }

        stopServerTask = getProxy().getScheduler().schedule(BungeeGCloud.this, () -> {
            getLogger().info("Stopping instance due to inactivity...");
            try {
                instance.stop();
                getLogger().info("Instance stopped");
            } catch (IOException | InterruptedException | ComputeException e) {
                getLogger().log(Level.SEVERE, "Failed to stop the instance", e);
            }
        }, STOP_SERVER_DELAY, TimeUnit.SECONDS);
    }

    public void cancelStopTask() {
        if (stopServerTask != null) {
            stopServerTask.cancel();
            stopServerTask = null;
        }
    }

    public ServerStatus getServerStatus() {
        return serverStatus;
    }
}
