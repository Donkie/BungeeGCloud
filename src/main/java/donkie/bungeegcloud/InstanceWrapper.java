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

    private void startMovedZones()
            throws IOException, InterruptedException, ComputeException, ZoneResourcePoolExhaustedException {
        Set<String> zonesToTestSet = new HashSet<String>(ZONES);
        zonesToTestSet.remove(zone);

        List<String> zonesToTest = new ArrayList<String>(zonesToTestSet);

        boolean success = false;
        for (String newZone : zonesToTest) {
            logger.info(String.format("Moving instance to zone %s...", newZone));
            move(newZone);
            zone = newZone;
            try {
                compute.startInstance(newZone, name);
            } catch (ZoneResourcePoolExhaustedException e) {
                logger.info(String.format("New zone %s is also exhausted.", newZone));
                continue;
            }
            logger.info(String.format("Zone %s is available!", newZone));
            success = true;
            break;
        }

        if (!success) {
            throw new ZoneResourcePoolExhaustedException("No zone with available resources found in the region :(");
        }
    }

    public void start() throws IOException, InterruptedException, ComputeException, ZoneResourcePoolExhaustedException {
        try {
            compute.startInstance(zone, name);
        } catch (ZoneResourcePoolExhaustedException e) {
            logger.warning(
                    String.format("Compute Engine resources exhausted in zone %s, starting relocation...", zone));
            startMovedZones();
        }
        ip = getInstanceExternalIP(fetchInstance());
        running = true;
    }

    public void stop() throws IOException, InterruptedException, ComputeException {
        running = false;
        ip = null;
        compute.stopInstance(zone, name);
    }

    public void move(String newZoneName) throws IOException, InterruptedException, ComputeException {
        compute.moveInstance(zone, newZoneName, name);
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
