package donkie.bungeegcloud;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
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
                machine.updateRunningStatus();
                if (!machine.isRunning()) {
                    status.setOffline();
                    throw new NotOnlineException();
                }

                ipport = new IPPort(machine.getIp(), getServerPort());
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
                        .info(String.format("No players online. Stopping server in %d seconds...", idleServerStopwait));
                startStopTask();
            } else if (onlinePlayers > 0 && stopServerTask != null) {
                getLogger().info("Players now online, aborting stop.");
                cancelStopTask();
            }
        }
    }

    private int minecraftPort;

    /**
     * How often we should check for if there are any players online
     */
    private int refreshPlayersPeriod;

    /**
     * How long we should wait until stopping the server after last player left
     */
    private long idleServerStopwait;

    private ScheduledTask stopServerTask = null;

    private ServerStatus serverStatus;

    private boolean serverStarting = false;

    private Machine machine;

    @Override
    public void onEnable() {
        Configuration configuration;
        try{
            if (!getDataFolder().exists())
                getDataFolder().mkdir();

            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e){
            getLogger().log(Level.SEVERE, "Failed to load config, is config.yml missing?", e);
            return;
        }

        Configuration minecraftConfig = configuration.getSection("minecraft");
        minecraftPort = minecraftConfig.getInt("port");

        Configuration minecraftDefaultsConfig = minecraftConfig.getSection("default");
        String defaultMOTD = minecraftDefaultsConfig.getString("motd");
        int defaultMaxPlayers = minecraftDefaultsConfig.getInt("max_players");
        serverStatus = new ServerStatus(defaultMaxPlayers, defaultMOTD);

        idleServerStopwait = configuration.getLong("idle_server_stopwait");
        refreshPlayersPeriod = configuration.getInt("refresh_players_period");

        if(minecraftPort == 0 || defaultMOTD.isEmpty() || defaultMaxPlayers == 0 || refreshPlayersPeriod == 0){
            getLogger().log(Level.SEVERE, "Failed to load config, some config items are missing!");
            return;
        }

        try {
            machine = new ComputeInstance(configuration.getSection("compute"), getDataFolder());
        } catch (GeneralSecurityException | IOException e) {
            getLogger().log(Level.SEVERE, "Failed to setup compute engine", e);
            return;
        }

        getProxy().getScheduler().schedule(this, new ServerQueryRunnable(), 1, refreshPlayersPeriod, TimeUnit.SECONDS);
        getProxy().getPluginManager().registerListener(this, new Events(this));
    }

    public boolean isServerRunning() {
        try {
            MCQuery query = new MCQuery(machine.getIp(), minecraftPort);
            query.basicStat();
            return true;
        } catch (NotOnlineException | IOException e) {
            return false;
        }
    }

    public Machine getMachine() {
        return machine;
    }

    public ServerInfo getServerInfo() throws NotOnlineException {
        return getServerInfo(new IPPort(machine.getIp(), minecraftPort));
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
        return minecraftPort;
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
                machine.stop();
                getLogger().info("Instance stopped");
            } catch (IOException | InterruptedException | ServiceException e) {
                getLogger().log(Level.SEVERE, "Failed to stop the instance", e);
            }
        }, idleServerStopwait, TimeUnit.SECONDS);
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
