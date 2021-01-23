package donkie.bungeegcloud;

import java.io.IOException;

/**
 * Represents a generic start/stoppable machine that can host a Minecraft server
 */
public interface Machine {
    public void start() throws IOException, InterruptedException, ServiceException, MachinePoolExhaustedException;
    public void stop() throws IOException, InterruptedException, ServiceException;
	public boolean isRunning();
	public void updateRunningStatus() throws IOException;
	public String getIp() throws NotOnlineException;
}
