package donkie.bungeegcloud;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.Compute.Instances.AggregatedList;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceAggregatedList;
import com.google.api.services.compute.model.InstanceMoveRequest;
import com.google.api.services.compute.model.InstancesScopedList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Operation.Error;
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
        GoogleCredentials credential = GoogleCredentials.fromStream(new FileInputStream(GCLOUD_CREDENTIALS));
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

    public String getInstanceZone(String instanceName) throws IOException {
        AggregatedList lister = compute.instances().aggregatedList(projectId);
        InstanceAggregatedList list = lister.execute();
        for (Map.Entry<String, InstancesScopedList> entry : list.getItems().entrySet()) {
            List<Instance> instances = entry.getValue().getInstances();
            if (instances != null) {
                for (Instance instance : instances) {
                    if (instance.getName().equals(instanceName)) {
                        return entry.getKey().substring(6);
                    }
                }
            }
        }
        throw new FileNotFoundException(
                String.format("Couldn't find any instance named \"%s\" in the project!", instanceName));
    }

    /**
     * Fires up the instance
     *
     * @param zoneName
     * @param instanceName
     * @throws IOException
     * @throws InterruptedException
     * @throws ComputeException
     */
    public void startInstance(String zoneName, String instanceName)
            throws IOException, InterruptedException, ComputeException, ZoneResourcePoolExhaustedException {
        Compute.Instances.Start starter = compute.instances().start(projectId, zoneName, instanceName);
        Operation op = starter.execute();

        try {
            blockUntilComplete(op, 5 * 60 * 1000L);
        } catch (ComputeException e) {
            if (e.getCode().equals("ZONE_RESOURCE_POOL_EXHAUSTED")) {
                throw new ZoneResourcePoolExhaustedException(e.getMessage());
            } else {
                throw e;
            }
        }
    }

    /**
     * Stops the instance
     *
     * @param zoneName
     * @param instanceName
     * @throws IOException
     * @throws InterruptedException
     * @throws ComputeException
     */
    public void stopInstance(String zoneName, String instanceName)
            throws IOException, InterruptedException, ComputeException {
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
     * @throws ComputeException
     */
    public void blockUntilComplete(Operation operation, long timeout)
            throws InterruptedException, IOException, ComputeException {
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
        if (operation != null && operation.getError() != null) {
            throwOperationException(operation.getError());
        }
    }

    private void throwOperationException(Error err) throws ComputeException {
        if (err.getErrors().size() == 0) {
            throw new ComputeException();
        }

        Error.Errors error = err.getErrors().get(0);
        throw new ComputeException(String.format("[%s] %s", error.getCode(), error.getMessage()), error.getCode(),
                error.getMessage());
    }
}
