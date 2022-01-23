import java.util.UUID;

public class Context {

    final UUID serverId;
    volatile UUID currentLeader = null;

    public Context(UUID serverId) {
        this.serverId = serverId;
    }

    public synchronized boolean isLeader() {
        return serverId.equals(currentLeader);
    }
}
