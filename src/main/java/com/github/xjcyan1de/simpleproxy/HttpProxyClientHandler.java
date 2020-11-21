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

        String[] arr = httpRequest.headers().get("Host").split(":");
        String host = arr[0].trim();
        int port;
        if (arr.length == 3) {
            port = Integer.parseInt(arr[1]);
        } else if (isHttps) {
            port = 443;
        } else {
            port = 80;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientChannel.eventLoop())
                .channel(clientChannel.getClass())
                .handler(new HttpProxyRemoteHandler(clientChannel));
        ChannelFuture channelFuture = bootstrap.connect(host, port);
        remoteChannel = channelFuture.channel();

        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                clientChannel.config().setAutoRead(true);
                if (!isHttps) {
                    remoteChannel.writeAndFlush(msg);
                }
            } else {
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
