import java.net.*;
import java.util.UUID;

public class Server {

    public static void main(String[] args) throws SocketException, UnknownHostException {
        Context ctx = new Context(UUID.randomUUID());
        System.out.println("Starting [" + ctx.serverId + "] node");
        new DiscoveryService(ctx).start();
    }
}