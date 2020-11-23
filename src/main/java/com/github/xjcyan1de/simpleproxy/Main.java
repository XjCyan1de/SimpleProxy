package com.github.xjcyan1de.simpleproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.IOException;
import java.util.function.Supplier;

public class Main {

    public static final int PROXY_PORT = SystemPropertyUtil.getInt("port", 8080);

    private static final boolean epollEnabled = Epoll.isAvailable();

    private static final boolean ioUringEnabled =
            SystemPropertyUtil.getBoolean("io_uring", false);
//                    && IOUring.isAvailable();

    private static final Class<? extends ServerChannel> serverSocketChannelImpl =
//            ioUringEnabled ? IOUringServerSocketChannel.class :
            epollEnabled ? EpollServerSocketChannel.class :
                    NioServerSocketChannel.class;

    private static final Supplier<EventLoopGroup> eventLoopGroupImpl = () ->
//            ioUringEnabled ? new IOUringEventLoopGroup() :
            epollEnabled ? new EpollEventLoopGroup() :
                    new NioEventLoopGroup();

    static {
//        System.out.println("IOUring.isAvailable(): " + IOUring.isAvailable());
        System.out.println("-Dio_uring: " + SystemPropertyUtil.getBoolean("io_uring", false));
        System.out.println("Epoll.isAvailable(): " + Epoll.isAvailable());
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String address = "[2a02:180:6:1::af9]";
        HostAndPort hostAndPort = HostAndPort.fromString(address).withDefaultPort(80);
        Bootstrap bootstrap = new Bootstrap()
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new HttpRequestEncoder())
                                .addLast(new HttpResponseDecoder())
                                .addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        System.out.println(msg);
                                    }
                                });
                    }
                })
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .remoteAddress(hostAndPort.getHost(), hostAndPort.getPort());
//        bootstrap.connect().sync().channel().writeAndFlush(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://"+address));


        EventLoopGroup bossGroup = eventLoopGroupImpl.get();
        EventLoopGroup workerGroup = eventLoopGroupImpl.get();
        try {
            System.out.println("Starting proxy on port: " + PROXY_PORT + " using " + bossGroup.getClass().getSimpleName());
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(serverSocketChannelImpl)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast("decoder", new HttpRequestDecoder());
                            ch.pipeline().addLast("encoder", new HttpResponseEncoder());
                            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
                            ch.pipeline().addLast(new HttpProxyClientHandler());
                        }
                    })
                    .bind(PROXY_PORT).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
