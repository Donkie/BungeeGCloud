package donkie.bungeegcloud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZoneResourcePoolExhaustedException extends Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private static final String ZONE_CAPTURE_REGEX = "The zone '[\\w\\/-]+zones\\/([\\w-]+)' does not";

    private final String zoneId;

    public ZoneResourcePoolExhaustedException(String msg){
        super(msg);

        final Pattern pattern = Pattern.compile(ZONE_CAPTURE_REGEX);
        final Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            zoneId = matcher.group(1);
        } else {
            zoneId = null;
        }
    }

    public String getZoneId(){
        return zoneId;
    }
}
