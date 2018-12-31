package com.waben.stock.futuresgateway.yisheng.server.ws;

import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channel Manager
 *
 */

@Component("channelRepositoryForWs")
public class ChannelRepository {
	public final static Map<String, Channel> channelCache = new ConcurrentHashMap<String, Channel>();

	public void put(String key, Channel value) {
		channelCache.put(key, value);
	}

	public Channel get(String key) {
		return channelCache.get(key);
	}

	public void remove(String key) { 
		channelCache.remove(key);
	}

	public int size() {
		return channelCache.size();
	}
}
