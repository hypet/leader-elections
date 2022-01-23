import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class DiscoveryClient implements Runnable {

    private final Collection<InetSocketAddress> pingAddressList;
    private final Context ctx;
    private final Map<Channel, InetAddress> multicastChannelMap;

    public DiscoveryClient(Context ctx, Map<Channel, InetAddress> multicastChannelMap, Collection<InetSocketAddress> pingAddressList) {
        this.ctx = ctx;
        this.multicastChannelMap = multicastChannelMap;
        this.pingAddressList = pingAddressList;
    }

    public void run() {
        System.out.println("Starting discovery client...");
        EventLoopGroup group = new NioEventLoopGroup();
        ByteBuf data = makeDiscoveryMessage(pingAddressList);
        try {
            while (true) {
                multicastChannelMap.forEach((channel, address) -> {
                    try {
                        channel.writeAndFlush(new DatagramPacket(
                                data.copy(),
                                new InetSocketAddress(InetAddress.getByName(Misc.MULTICAST_GROUP), Misc.DISCOVERY_PORT),
                                new InetSocketAddress(address, Misc.DISCOVERY_PORT))
                        ).sync();
                    } catch (UnknownHostException | InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                Thread.sleep(Misc.DISCOVERY_PERIOD_MS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("Stopping discovery client...");
            group.shutdownGracefully();
        }
    }

    private ByteBuf makeDiscoveryMessage(Collection<InetSocketAddress> addressList) {
        String addresses = addressList.stream()
                .map(address -> address.getAddress().getHostAddress() + ":" + address.getPort())
                .collect(Collectors.joining(","));
        return Unpooled.copiedBuffer(Misc.DG_DISCOVERY_HEADER
                + Misc.DG_DELIMITER + ctx.serverId
                + Misc.DG_DELIMITER + addresses, CharsetUtil.UTF_8);
    }
}