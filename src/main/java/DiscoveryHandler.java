import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ChannelHandler.Sharable
public class DiscoveryHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final Context ctx;
    private final DiscoveryRegistry discoveryRegistry;

    public DiscoveryHandler(Context ctx, DiscoveryRegistry discoveryRegistry) {
        this.ctx = ctx;
        this.discoveryRegistry = discoveryRegistry;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        try {
            ByteBuf content = msg.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.duplicate().readBytes(bytes);
            String data = new String(bytes);
            String[] splitResult = data.split(Misc.DG_DELIMITER);
            String datagramHeader = splitResult[0];
            switch (datagramHeader) {
                case Misc.DG_DISCOVERY_HEADER:
                    processDiscoveryMsg(UUID.fromString(splitResult[1]), splitResult[2]);
                    break;
                case Misc.DG_ELECT_LEADER_HEADER:
                    elections();
                    break;
                default:
                    System.out.println("Unknown message " + data + " from " + msg.sender());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processDiscoveryMsg(UUID id, String availableAddresses) {
        if (id.equals(ctx.serverId)) {
            return;
        }

        String[] addresses = availableAddresses.split(",");

        synchronized (discoveryRegistry) {
            if (!discoveryRegistry.exists(id)) {
                System.out.println("[Discovery] New node: " + id + " with addresses: " + availableAddresses);
                Set<InetSocketAddress> addressSet = Arrays.stream(addresses)
                        .filter(Objects::nonNull)
                        .map(address -> {
                            String[] hostAndPort = address.split(":");
                            return new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
                        }).collect(Collectors.toSet());
                discoveryRegistry.register(id, addressSet);

                elections();
            }
        }
    }

    public void elections() {
        UUID leader = electLeader();
        ctx.currentLeader = leader;
        System.out.println("Leader: " + leader);
        if (ctx.isLeader()) {
            System.out.println("I'm leader");
        } else {
            System.out.println("I'm not leader");
        }
    }

    private UUID electLeader() {
        if (discoveryRegistry.getRegistry().size() < 1) {
            return null;
        }

        Set<UUID> uuidSet = new HashSet<>(discoveryRegistry.getRegistry().keySet());
        uuidSet.add(ctx.serverId);

        return uuidSet.stream()
                .max(Comparator.naturalOrder())
                .get();
    }

}