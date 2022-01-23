import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

import java.util.concurrent.atomic.AtomicLong;

public class PingHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final DiscoveryRegistry discoveryRegistry;
    private final AtomicLong lastPingTimestamp;

    public PingHandler(DiscoveryRegistry discoveryRegistry, AtomicLong lastPingTimestamp) {
        this.discoveryRegistry = discoveryRegistry;
        this.lastPingTimestamp = lastPingTimestamp;
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
            System.out.println("[Ping handler] Content: " + data + " from " + msg.sender());

            switch (data) {
                case Misc.PING:
                    lastPingTimestamp.set(System.currentTimeMillis());
                    ctx.channel().writeAndFlush(new DatagramPacket(
                            Unpooled.copiedBuffer(Misc.PONG, CharsetUtil.UTF_8),
                            msg.sender())).sync();
                    break;
                case Misc.PONG:
                    discoveryRegistry.setPingTimestamp(msg.sender(), 0L);
                    break;
                default:
                    System.out.println("Unknown message " + data +  " from " + msg.sender());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}