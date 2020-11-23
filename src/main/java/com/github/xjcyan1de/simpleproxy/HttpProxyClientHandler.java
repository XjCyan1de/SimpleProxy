package com.github.xjcyan1de.simpleproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

    private boolean isHttps = false;
    private Channel clientChannel;
    private Channel remoteChannel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (remoteChannel != null) {
            remoteChannel.writeAndFlush(msg);
            return;
        }

        clientChannel.config().setAutoRead(false);

        HttpRequest httpRequest = (HttpRequest) msg;
        HttpMethod method = httpRequest.method();
        if (method == HttpMethod.CONNECT) {
            isHttps = true;
            clientChannel.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            clientChannel.pipeline().remove("decoder");
            clientChannel.pipeline().remove("encoder");
            clientChannel.pipeline().remove("aggregator");
        }

        System.out.println(httpRequest);
        String hostHeader = httpRequest.headers().get("Host");
        HostAndPort hostAndPort = HostAndPort.fromString(hostHeader).withDefaultPort(80);

        System.out.println("Connect " + hostHeader + " " + hostAndPort);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientChannel.eventLoop())
                .channel(clientChannel.getClass())
                .remoteAddress(hostAndPort.getHost(), hostAndPort.getPort())
                .handler(new HttpProxyRemoteHandler(clientChannel));
        ChannelFuture channelFuture = bootstrap.connect();
        remoteChannel = channelFuture.channel();

        channelFuture.addListener(future -> {
            System.out.println(hostAndPort + " Is connected: " + future.isSuccess());
            if (future.isSuccess()) {
                clientChannel.config().setAutoRead(true);
                if (!isHttps) {
                    remoteChannel.writeAndFlush(msg);
                }
            } else {
                clientChannel.close();
                future.cause().printStackTrace();
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
