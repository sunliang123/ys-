package com.waben.stock.futuresgateway.yisheng.server.ws;

import com.waben.stock.futuresgateway.yisheng.server.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component("tcpServerForWs")
public class TCPServer {

	@Autowired
	@Qualifier("serverChannelInitializerForWs")
	private ServerChannelInitializer serverChannelInitializer;

	private Channel serverChannel;

	public void start() throws Exception {
		ServerBootstrap b = new ServerBootstrap();
		b.group(new NioEventLoopGroup(2), new NioEventLoopGroup(2))
				.channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.DEBUG))
				.childHandler(serverChannelInitializer);
		Map<ChannelOption<?>, Object> options = new HashMap<ChannelOption<?>, Object>();
		options.put(ChannelOption.SO_KEEPALIVE, true);
		options.put(ChannelOption.SO_BACKLOG, 100);
		Map<ChannelOption<?>, Object> tcpChannelOptions = options;
		Set<ChannelOption<?>> keySet = tcpChannelOptions.keySet();
		for (@SuppressWarnings("rawtypes") ChannelOption option : keySet) {
			b.option(option, tcpChannelOptions.get(option));
		}
		serverChannel =  b.bind(new InetSocketAddress(9095)).sync().channel().closeFuture().sync().channel();
	}

	@PreDestroy
	public void stop() throws Exception {
		serverChannel.close();
		serverChannel.parent().close();
	}




}
