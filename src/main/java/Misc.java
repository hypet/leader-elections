import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Misc {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(4);

    public static final String DG_DELIMITER = " ";
    public static final String DG_DISCOVERY_HEADER = "DISC";
    public static final String DG_ELECT_LEADER_HEADER = "ELECT";
    public static final String MULTICAST_GROUP = "239.15.15.15";  // Any of 224.0.0.0 - 239.255.255.255
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final long DISCOVERY_PERIOD_MS = 30_000L;
    public static final long PING_PERIOD_MS = 5000L;
    public static final long PING_TIMEOUT_MS = 3 * PING_PERIOD_MS;
    public static final long LEADER_ABSENCE_TIMEOUT = 2 * PING_TIMEOUT_MS;
    public static final int DISCOVERY_PORT = 55100;

}