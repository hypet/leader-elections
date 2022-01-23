import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class DiscoveryService {

    private final DiscoveryRegistry discoveryRegistry = new DiscoveryRegistry();
    private final Map<NetworkInterface, InetAddress> interfaces;
    private final Context ctx;

    public DiscoveryService(Context ctx) throws SocketException, UnknownHostException {
        this.ctx = ctx;
        this.interfaces = getInterfaces();
    }

    public void start() {
        System.out.println("Starting discovery server...");

        EventLoopGroup group = new NioEventLoopGroup();
        Map<Channel, InetAddress> multicastChannelMap = new HashMap<>();
        interfaces.forEach((iface, address) -> {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channelFactory(() -> new NioDatagramChannel(InternetProtocolFamily.IPv4))
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.IP_MULTICAST_IF, iface)
                    .handler(new DiscoveryHandler(ctx, discoveryRegistry));

            try {
                NioDatagramChannel ch = (NioDatagramChannel) bootstrap.bind("0.0.0.0", Misc.DISCOVERY_PORT).sync().channel();
                ch.joinGroup(InetAddress.getByName(Misc.MULTICAST_GROUP)).sync();

                multicastChannelMap.put(ch, InetAddress.getByAddress(address.getAddress()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        PingService pingService = new PingService(ctx, discoveryRegistry, interfaces);
        Misc.EXECUTOR_SERVICE.execute(new DiscoveryClient(ctx, multicastChannelMap, pingService.getPingAddressList()));
        Misc.EXECUTOR_SERVICE.execute(pingService);

        multicastChannelMap.forEach((ch, address) -> {
            try {
                ch.closeFuture().await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private Map<NetworkInterface, InetAddress> getInterfaces() throws SocketException, UnknownHostException {
        Map<NetworkInterface, InetAddress> resultMap = new HashMap<>();
        for (Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
            NetworkInterface iface = ifaces.nextElement();
            if (iface.supportsMulticast() && iface.isUp() && !iface.isLoopback()) {
                Optional<InetAddress> ipv4Address = iface.getInterfaceAddresses().stream()
                        .map(InterfaceAddress::getAddress)
                        .filter(a -> a.getClass() == Inet4Address.class && !a.isLoopbackAddress())
                        .findFirst();
                if (ipv4Address.isPresent()) {
                    InetAddress address = ipv4Address.get();
                    System.out.println("IF: " + address
                            + ", multicast: " + iface.supportsMulticast()
                            + ", up: " + iface.isUp()
                            + ", lo: " + iface.isLoopback()
                    );

                    resultMap.put(iface, InetAddress.getByAddress(address.getAddress()));
                }
            }
        }
        return resultMap;
    }

}