package com.github.xjcyan1de.simpleproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

    private final HttpProxyClientHeader header = new HttpProxyClientHeader();
    private Channel clientChannel;
    private Channel remoteChannel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (header.isComplete()) {
            remoteChannel.writeAndFlush(msg); // just forward
            return;
        }

        ByteBuf input = (ByteBuf) msg;
        header.digest(input);

        if (!header.isComplete()) {
            input.release();
            return;
        }

        clientChannel.config().setAutoRead(false); // disable AutoRead until remote connection is ready

        if (header.isHttps()) { // if https, respond 200 to create tunnel
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes()));
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientChannel.eventLoop()) // use the same EventLoop
                .channel(clientChannel.getClass())
                .handler(new HttpProxyRemoteHandler(clientChannel));
        ChannelFuture channelFuture = bootstrap.connect(header.getHost(), header.getPort());
        remoteChannel = channelFuture.channel();

        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                clientChannel.config().setAutoRead(true);
                if (!header.isHttps()) {
                    remoteChannel.write(header.getByteBuf());
                }
                remoteChannel.writeAndFlush(input);
            } else {
                input.release();
                clientChannel.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        e.printStackTrace();
        flushAndClose(clientChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
