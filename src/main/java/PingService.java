import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class PingService implements Runnable {

    private final AtomicLong lastPingTimestamp = new AtomicLong(0);
    private final Context ctx;
    private final DiscoveryRegistry discoveryRegistry;
    private NioDatagramChannel channel;
    private List<InetSocketAddress> pingAddresses;

    public PingService(Context ctx, DiscoveryRegistry discoveryRegistry, Map<NetworkInterface, InetAddress> interfaces) {
        this.ctx = ctx;
        this.discoveryRegistry = discoveryRegistry;

        EventLoopGroup group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channelFactory(() -> new NioDatagramChannel(InternetProtocolFamily.IPv4))
                .handler(new PingHandler(discoveryRegistry, lastPingTimestamp));
        try {
            // Bind to a random port
            channel = (NioDatagramChannel) bootstrap.bind("0.0.0.0", 0).sync().channel();

            System.out.println("Ping port: " + channel.localAddress().getPort());

            pingAddresses = interfaces.values().stream()
                    .map((address) -> new InetSocketAddress(address, channel.localAddress().getPort()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Collection<InetSocketAddress> getPingAddressList() {
        return pingAddresses;
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (ctx.isLeader()) {
                    pingNodes();
                } else {
                    checkLeaderPresence();
                }

                Thread.sleep(Misc.PING_PERIOD_MS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void pingNodes() {
        Map<UUID, Set<InetSocketAddress>> addressMapToRemove = new HashMap<>();
        // Ping each address of every known node
        discoveryRegistry.getRegistry().forEach((id, addresses) -> {
            addresses.forEach(address -> {
                long pingElapsedMillis = discoveryRegistry.getPingElapsedMillis(address);
                if (pingElapsedMillis >= 0 && pingElapsedMillis < Misc.PING_TIMEOUT_MS) {
                    System.out.println("Pinging " + id + " with address: " + address + ", last ping: " + pingElapsedMillis + "ms");
                    try {
                        channel.writeAndFlush(new DatagramPacket(
                                Unpooled.copiedBuffer(Misc.PING, CharsetUtil.UTF_8),
                                address)
                        ).sync();

                        if (pingElapsedMillis == 0) {
                            discoveryRegistry.addPingCurrentTimestamp(address);
                        }
                    } catch (Exception e) {
                        System.out.println("Error while pinging " + address + " from channel " + channel + ": " + e.getMessage());
                    }
                } else {
                    Set<InetSocketAddress> set = addressMapToRemove.computeIfAbsent(id, k -> new HashSet<>());
                    set.add(address);
                }
            });
        });

        // Remove unresponsive addresses
        addressMapToRemove.forEach((id, addresses) -> {
            addresses.forEach(address -> {
                System.out.println("Removing " + address);
                discoveryRegistry.removeAddress(id, address);
            });
        });
    }

    private void checkLeaderPresence() {
        long millisFromLastPing = System.currentTimeMillis() - lastPingTimestamp.get();
        if (lastPingTimestamp.get() > 0 && millisFromLastPing > Misc.LEADER_ABSENCE_TIMEOUT) {
            if (ctx.currentLeader != null) {
                discoveryRegistry.unregister(ctx.currentLeader);
                ctx.currentLeader = null;
            }

            if (discoveryRegistry.getRegistry().size() > 0) {
                System.out.println("No ping from leader for a long time: " + millisFromLastPing + "ms.");
                try {
                    channel.writeAndFlush(new DatagramPacket(
                            Unpooled.copiedBuffer(Misc.DG_ELECT_LEADER_HEADER, CharsetUtil.UTF_8),
                            new InetSocketAddress(InetAddress.getByName(Misc.MULTICAST_GROUP), Misc.DISCOVERY_PORT))
                    ).sync();
                } catch (UnknownHostException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
