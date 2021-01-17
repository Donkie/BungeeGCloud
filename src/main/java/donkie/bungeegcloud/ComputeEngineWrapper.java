package donkie.bungeegcloud;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

public class ComputeEngineWrapper {

    /**
     * GCloud json credentials file
     */
    private static final String GCLOUD_CREDENTIALS = "exhale-290316-d81df81f2dfd.json";

    /**
     * Be sure to specify the name of your application. If the application name is
     * {@code null} or blank, the application will log a warning. Suggested format
     * is "MyCompany-ProductName/1.0".
     */
    private static final String APPLICATION_NAME = "Donkie-BungeeGCloud/1.0";

    private final String projectId;

    /** Global instance of the HTTP transport. */
    private NetHttpTransport httpTransport;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private Compute compute;

    public ComputeEngineWrapper(String projectId) throws GeneralSecurityException, IOException {
        this.projectId = projectId;

        httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Authenticate using Google Application Default Credentials.
        GoogleCredentials credential = GoogleCredentials
                .fromStream(new FileInputStream(GCLOUD_CREDENTIALS));
        if (credential.createScopedRequired()) {
            List<String> scopes = new ArrayList<>();
            // Set Google Cloud Storage scope to Full Control.
            scopes.add(ComputeScopes.DEVSTORAGE_FULL_CONTROL);
            // Set Google Compute Engine scope to Read-write.
            scopes.add(ComputeScopes.COMPUTE);
            credential = credential.createScoped(scopes);
        }
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credential);

        // Create Compute Engine object for listing instances.
        compute = new Compute.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME).build();
    }

    public Instance getInstance(String zoneName, String instanceName) throws IOException {
        Compute.Instances.Get getter = compute.instances().get(projectId, zoneName, instanceName);
        return getter.execute();
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

    /**
     * Fires up the instance
     *
     * @param zoneName
     * @param instanceName
     * @throws IOException
     * @throws InterruptedException
     */
    public void startInstance(String zoneName, String instanceName) throws IOException, InterruptedException {
        Compute.Instances.Start starter = compute.instances().start(projectId, zoneName, instanceName);
        Operation op = starter.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Stops the instance
     *
     * @param zoneName
     * @param instanceName
     * @throws IOException
     * @throws InterruptedException
     */
    public void stopInstance(String zoneName, String instanceName) throws IOException, InterruptedException {
        Compute.Instances.Stop stopper = compute.instances().stop(projectId, zoneName, instanceName);
        Operation op = stopper.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Wait until {@code operation} is completed.
     *
     * @param compute   the {@code Compute} object
     * @param operation the operation returned by the original request
     * @param timeout   the timeout, in millis
     * @return the error, if any, else {@code null} if there was no error
     * @throws InterruptedException if we timed out waiting for the operation to
     *                              complete
     * @throws IOException          if we had trouble connecting
     */
    public Operation.Error blockUntilComplete(Operation operation, long timeout)
            throws InterruptedException, IOException {
        long start = System.currentTimeMillis();
        final long pollInterval = 5 * 1000L;
        String zone = operation.getZone(); // null for global/regional operations
        if (zone != null) {
            String[] bits = zone.split("/");
            zone = bits[bits.length - 1];
        }
        String status = operation.getStatus();
        String opId = operation.getName();
        while (operation != null && !status.equals("DONE")) {
            Thread.sleep(pollInterval);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for operation to complete");
            }
            if (zone != null) {
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId, zone, opId);
                operation = get.execute();
            } else {
                Compute.GlobalOperations.Get get = compute.globalOperations().get(projectId, opId);
                operation = get.execute();
            }
            if (operation != null) {
                status = operation.getStatus();
            }
        }
        return operation == null ? null : operation.getError();
    }
}
