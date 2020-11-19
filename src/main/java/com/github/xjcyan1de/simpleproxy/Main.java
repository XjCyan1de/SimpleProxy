package com.github.xjcyan1de.simpleproxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.incubator.channel.uring.IOUring;
import io.netty.incubator.channel.uring.IOUringServerSocketChannel;
import io.netty.util.internal.SystemPropertyUtil;

public class Main {

    public static final int PROXY_PORT = 8080;

    private static final boolean epollEnabled = Epoll.isAvailable();

    private static final boolean ioUringEnabled =
            SystemPropertyUtil.getBoolean("io_uring", false)
                    && IOUring.isAvailable();

    private static final Class<? extends ServerChannel> serverSocketChannelImpl =
            ioUringEnabled ? IOUringServerSocketChannel.class :
                    epollEnabled ? EpollServerSocketChannel.class :
                            NioServerSocketChannel.class;

    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            System.out.println("Starting proxy on port: " + PROXY_PORT);
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(serverSocketChannelImpl)
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            System.out.println("Connected: " + ch.pipeline().channel());
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
