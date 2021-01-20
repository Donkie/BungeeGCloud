package donkie.bungeegcloud;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;

public class InstanceWrapper {
    private String name;
    private String zone;
    private String ip = null;
    private boolean running = false;

    private ComputeEngineWrapper compute;
    private Logger logger;

    private static final Set<String> ZONES = new HashSet<String>(
            Arrays.asList("europe-west2-a", "europe-west2-b", "europe-west2-c"));

    public InstanceWrapper(String name, ComputeEngineWrapper compute, Logger logger) {
        this.name = name;
        this.compute = compute;
        this.logger = logger;
    }

    public Instance fetchInstance() throws IOException {
        return compute.getInstance(zone, name);
    }

    public void fetchZone() throws IOException {
        zone = compute.getInstanceZone(name);
    }

    public void updateRunningStatus() throws IOException {
        running = isRunningStatus(fetchInstance().getStatus());
        if (!running) {
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

    public void start() throws IOException, InterruptedException, ComputeException, ZoneResourcePoolExhaustedException {
        compute.startInstance(zone, name);
        ip = getInstanceExternalIP(fetchInstance());
        running = true;
    }

    public void stop() throws IOException, InterruptedException, ComputeException {
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
