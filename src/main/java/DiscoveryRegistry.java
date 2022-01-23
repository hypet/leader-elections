import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoveryRegistry {

    private final Map<UUID, Set<InetSocketAddress>> registry = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, Long> pingTimestamps = new ConcurrentHashMap<>();

    public void register(UUID serverId, Set<InetSocketAddress> addresses) {
        registry.put(serverId, addresses);
    }

    public void unregister(UUID serverId) {
        registry.remove(serverId);
    }

    public void removeAddress(UUID serverId, InetSocketAddress address) {
        Set<InetSocketAddress> set = registry.get(serverId);
        if (set != null) {
            set.remove(address);
        }
    }

    public boolean exists(UUID serverId) {
        return registry.containsKey(serverId);
    }

    public Map<UUID, Set<InetSocketAddress>> getRegistry() {
        return Collections.unmodifiableMap(registry);
    }

    public void addPingCurrentTimestamp(InetSocketAddress address) {
        pingTimestamps.put(address, System.currentTimeMillis());
    }

    public void setPingTimestamp(InetSocketAddress address, long timestamp) {
        pingTimestamps.put(address, timestamp);
    }

    public long getPingElapsedMillis(InetSocketAddress address) {
        Long timestamp = pingTimestamps.get(address);
        return timestamp != null && timestamp > 0L ? System.currentTimeMillis() - timestamp : 0;
    }
}
