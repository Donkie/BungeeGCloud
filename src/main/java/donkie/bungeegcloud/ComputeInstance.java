package donkie.bungeegcloud;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;

import net.md_5.bungee.config.Configuration;

public class ComputeInstance implements Machine {
    private String name;
    private String zone;
    private String ip = null;
    private boolean running = false;

    private ComputeEngineWrapper compute;

    public ComputeInstance(Configuration computeConfig, File dataFolder)
            throws IOException, GeneralSecurityException {
        String projectId = computeConfig.getString("project_id");
        this.name = computeConfig.getString("instance_id");
        this.compute = new ComputeEngineWrapper(projectId, new File(dataFolder, "credentials.json"));

        fetchZone();
    }

    public Instance fetchInstance() throws IOException {
        return compute.getInstance(zone, name);
    }

    public void fetchZone() throws IOException {
        zone = compute.getInstanceZone(name);
    }

    public void updateRunningStatus() throws IOException {
        Instance instance = fetchInstance();
        running = isRunningStatus(instance.getStatus());
        if (running && ip == null) {
            // This can happen if the instance is started by something else, that means we need to fetch the ip now
            ip = getInstanceExternalIP(instance);
        }
        else if (!running) {
            ip = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getName() {
        return name;
    }

    public String getZone() {
        return zone;
    }

    public String getIp() throws NotOnlineException {
        if (ip == null || !running) {
            throw new NotOnlineException();
        }
        return ip;
    }

    public void start() throws IOException, InterruptedException, ServiceException, MachinePoolExhaustedException {
        compute.startInstance(zone, name);
        ip = getInstanceExternalIP(fetchInstance());
        running = true;
    }

    public void stop() throws IOException, InterruptedException, ServiceException {
        running = false;
        ip = null;
        compute.stopInstance(zone, name);
    }

    public static boolean isRunningStatus(String status) {
        return !(status.equals("STOPPING") || status.equals("SUSPENDING") || status.equals("SUSPENDED")
                || status.equals("TERMINATED"));
    }

    public static String getInstanceExternalIP(Instance instance) throws IOException {
        for (NetworkInterface nif : instance.getNetworkInterfaces()) {
            for (AccessConfig conf : nif.getAccessConfigs()) {
                if (conf.getType().equals("ONE_TO_ONE_NAT") && conf.getName().equals("External NAT")) {
                    return conf.getNatIP();
                }
            }
        }
        throw new FileNotFoundException("No external IP found for this instance");
    }
}
