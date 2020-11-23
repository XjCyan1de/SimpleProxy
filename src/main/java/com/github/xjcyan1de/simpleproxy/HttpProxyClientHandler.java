package com.github.xjcyan1de.simpleproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;

public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

    private Channel clientChannel;
    private Channel remoteChannel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        if (remoteChannel != null) {
            remoteChannel.writeAndFlush(msg);
            return;
        }

        HttpRequest httpRequest = (HttpRequest) msg;
        HttpMethod method = httpRequest.method();
        if (method == HttpMethod.CONNECT) {
            clientChannel.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
            clientChannel.pipeline().remove("decoder");
            clientChannel.pipeline().remove("encoder");
            clientChannel.pipeline().remove("aggregator");
        }

        HostAndPort hostAndPort = HostAndPort.fromString(httpRequest.headers().get("Host")).withDefaultPort(80);

        remoteChannel = new Bootstrap().group(new NioEventLoopGroup()).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                if (method != HttpMethod.CONNECT) {
                    ch.pipeline()
                            .addLast(new HttpRequestEncoder())
                            .addLast(new HttpResponseDecoder())
                            .addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                }
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        clientChannel.writeAndFlush(msg);
                    }
                });
            }
        }).remoteAddress(hostAndPort.getHost(), hostAndPort.getPort()).connect().sync().channel();

        if (method != HttpMethod.CONNECT) {
            remoteChannel.writeAndFlush(httpRequest);
        }
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
