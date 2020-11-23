package com.github.xjcyan1de.simpleproxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.incubator.channel.uring.IOUring;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import io.netty.incubator.channel.uring.IOUringServerSocketChannel;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.function.Supplier;

public class Main {

    public static final int PROXY_PORT = SystemPropertyUtil.getInt("port", 8080);

    private static final boolean epollEnabled = Epoll.isAvailable();

    private static final boolean ioUringEnabled =
            SystemPropertyUtil.getBoolean("io_uring", false)
                    && IOUring.isAvailable();

    private static final Class<? extends ServerChannel> serverSocketChannelImpl =
            ioUringEnabled ? IOUringServerSocketChannel.class :
                    epollEnabled ? EpollServerSocketChannel.class :
                            NioServerSocketChannel.class;

    private static final Supplier<EventLoopGroup> eventLoopGroupImpl = () ->
            ioUringEnabled ? new IOUringEventLoopGroup() :
                    epollEnabled ? new EpollEventLoopGroup() :
                            new NioEventLoopGroup();

    public static void main(String[] args) {
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

    static {
        System.out.println("IOUring.isAvailable(): " + IOUring.isAvailable());
        System.out.println("-Diouring.enabled: " + SystemPropertyUtil.getBoolean("iouring.enabled", false));
        System.out.println("Epoll.isAvailable(): " + Epoll.isAvailable());
    }
}
