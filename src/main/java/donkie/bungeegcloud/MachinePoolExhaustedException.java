package donkie.bungeegcloud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MachinePoolExhaustedException extends Exception {
    private static final long serialVersionUID = 1L;

    private static final String ZONE_CAPTURE_REGEX = "The zone '[\\w\\/-]+zones\\/([\\w-]+)' does not";

    private final String zoneId;

    public MachinePoolExhaustedException(String msg){
        super(msg);

        this.zoneId = null;
    }

    public MachinePoolExhaustedException(String msg, String zoneId){
        super(msg);

        this.zoneId = zoneId;
    }

    public String getZoneId(){
        return zoneId;
    }

    public static MachinePoolExhaustedException FromComputeMessage(String msg){
        final Pattern pattern = Pattern.compile(ZONE_CAPTURE_REGEX);
        final Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            return new MachinePoolExhaustedException(msg, matcher.group(1));
        } else {
            return new MachinePoolExhaustedException(msg);
        }
    }
}
