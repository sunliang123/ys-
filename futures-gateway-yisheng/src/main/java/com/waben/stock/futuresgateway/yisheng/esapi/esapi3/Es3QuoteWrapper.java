package com.waben.stock.futuresgateway.yisheng.esapi.esapi3;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.future.api.es.external3.quote.QuoteApi;
import com.future.api.es.external3.quote.bean.MarketInfo;
import com.future.api.es.external3.quote.bean.StkData;
import com.future.api.es.external3.quote.bean.StkHisData;
import com.future.api.es.external3.quote.bean.StkTraceData;
import com.future.api.es.external3.quote.listener.QuoteApiListener;
import com.waben.stock.futuresgateway.yisheng.common.protobuf.Command;
import com.waben.stock.futuresgateway.yisheng.common.protobuf.FuturesQuoteData.FuturesQuoteDataBase;
import com.waben.stock.futuresgateway.yisheng.common.protobuf.FuturesQuoteFullData.FuturesQuoteFullDataBase;
import com.waben.stock.futuresgateway.yisheng.common.protobuf.FuturesQuoteSimpleData.FuturesQuoteSimpleDataBase;
import com.waben.stock.futuresgateway.yisheng.common.protobuf.Message;
import com.waben.stock.futuresgateway.yisheng.common.protobuf.Message.MessageBase;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesContractDao;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesContract;
import com.waben.stock.futuresgateway.yisheng.esapi.EsEngine;
import com.waben.stock.futuresgateway.yisheng.esapi.schedule.QuoteDayKSchedule;
import com.waben.stock.futuresgateway.yisheng.rabbitmq.RabbitmqConfiguration;
import com.waben.stock.futuresgateway.yisheng.rabbitmq.RabbitmqProducer;
import com.waben.stock.futuresgateway.yisheng.rabbitmq.message.EsQuoteInfo;
import com.waben.stock.futuresgateway.yisheng.server.ChannelRepository;
import com.waben.stock.futuresgateway.yisheng.util.JacksonUtil;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

@Component("es3QuoteWrapper")
public class Es3QuoteWrapper implements QuoteApiListener {

	final Logger logger = LoggerFactory.getLogger(getClass());

	/** 行情IP */
	@Value("${es3.quote.ip}")
	private String quoteIp;
	/** 行情端口 */
	@Value("${es3.quote.port}")
	private short quotePort;
	/** 行情用户名 */
	@Value("${es3.quote.username}")
	private String quoteUsername;
	/** 行情密码 */
	@Value("${es3.quote.password}")
	private String quotePassword;
	/** 行情Token */
	@Value("${es3.quote.authcode}")
	private String quoteAuthCode;

	/**
	 * 行情api
	 */
	private QuoteApi api;

	/** 行情缓存 */
	private Map<String, StkData> quoteCache = new ConcurrentHashMap<>();

	@Autowired
	private RabbitmqProducer rabbitmqProducer;

	@Autowired
	private QuoteDayKSchedule dayKSchedule;

	@Autowired
	private FuturesContractDao contractDao;

	private final AttributeKey<Long> requestTypeInfo = AttributeKey.valueOf("requestTypeInfo");
	private final AttributeKey<String> hyInfo = AttributeKey.valueOf("hyInfo");
	private final AttributeKey<String> pzInfo = AttributeKey.valueOf("pzInfo");

	@Autowired
	@Qualifier("channelRepository")
	private ChannelRepository channelRepository;

	@Autowired
	@Qualifier("channelRepositoryForWs")
	private com.waben.stock.futuresgateway.yisheng.server.ws.ChannelRepository channelRepositoryForWs;

	/**
	 * 初始化
	 */
	public void init() {
		api = new QuoteApi(quoteAuthCode, "", true, false);
		api.setApiListener(this);
		connect();
	}

	/**
	 * 连接
	 */
	public void connect() {
		api.connect(quoteIp, quotePort);
		api.login(quoteUsername, quotePassword);
	}

	/**
	 * 重新连接
	 */
	public void reconnect() {
		try {
			api.disconnect();
			api.destory();
		} finally {
			logger.info("重新开始连接易盛3.0行情API。。。 ");
			init();
		}
	}

	/************************** 以下方法为回调方法 ***********************/

	@Override
	public void onChannelLost(int errorCode, String errorMsg) {
		logger.info("易盛3.0行情API断开连接{}_{} ", errorCode, errorMsg);
		this.connect();
	}

	@Override
	public void onRspHistoryQuot(StkHisData arg0) {

	}

	@Override
	public void onRspLogin(int errorCode, String errorMsg) {
		if (errorCode == 0) {
			logger.info("易盛3.0行情API登陆成功，{}_{}", errorCode, errorMsg);
		} else {
			logger.info("易盛3.0行情API登陆失败，{}_{}", errorCode, errorMsg);
		}
	}

	@Override
	public void onRspMarketInfo(MarketInfo marketInfo, boolean isLast) {
		logger.info("isLast：{}，易盛3.0行情API市场信息：{}", isLast, JacksonUtil.encode(marketInfo));
		if (isLast) {
			// 订阅行情
			List<FuturesContract> contractList = contractDao.retriveByEnable(true);
			if (contractList != null && contractList.size() > 0) {
				for (FuturesContract contract : contractList) {
					String stk = String.format("%s %s %s", contract.getExchangeNo(), contract.getCommodityNo(),
							contract.getContractNo());
					boolean subscribeResult = api.requestQuot("", stk, 1);
					if (subscribeResult) {
						logger.info("{}行情订阅成功!", stk);
					} else {
						logger.info("{}行情订阅失败!", stk);
					}
				}
			}
		}
	}

	public void subscribeQuote(String exchangeNo, String commodityNo, String contractNo) {
		String stk = String.format("%s %s %s", exchangeNo, commodityNo, contractNo);
		boolean subscribeResult = api.requestQuot("", stk, 1);
		if (subscribeResult) {
			logger.info("{}行情订阅成功!", stk);
		} else {
			logger.info("{}行情订阅失败!", stk);
		}
	}

	public void unsubscribeQuote(String exchangeNo, String commodityNo, String contractNo) {
		String stk = String.format("%s %s %s", exchangeNo, commodityNo, contractNo);
		boolean unsubscribeResult = api.requestQuot("", stk, 0);
		if (unsubscribeResult) {
			logger.info("{}行情取消订阅成功!", stk);
		} else {
			logger.info("{}行情取消订阅失败!", stk);
		}
	}

	@Override
	public void onRspTraceData(StkTraceData arg0) {

	}

	@Override
	public void onStkQuot(StkData stk) {
		logger.info("接收3.0行情：" + JacksonUtil.encode(stk));
		if (stk.getNew() > 0) {
			// 放入队列和缓存
			EsQuoteInfo quoteInfo = new EsQuoteInfo();
			quoteInfo.setInfo3(stk);
			quoteInfo.setApiType(3);
			quoteInfo.setQuoteIndex(dayKSchedule.getQuote3Index());
			dayKSchedule.increaseQuote3Index();
			rabbitmqProducer.sendMessage(RabbitmqConfiguration.quoteQueueName, quoteInfo);
			String[] combine = stk.getCode().split(" ");
			String commodityNo = combine[1];
			String contractNo = combine[2];
			String quoteCacheKey = getQuoteCacheKey(commodityNo, contractNo);
			quoteCache.put(quoteCacheKey, stk);
			// 推送行情
			SimpleDateFormat daySdf = new SimpleDateFormat("yyyy-MM-dd");
			String receive = new BigDecimal(stk.getUpdatetime()).setScale(0, RoundingMode.DOWN).toString();
			String dateTimeStamp = daySdf.format(new Date()) + " " + receive.substring(0, 2) + ":"
					+ receive.substring(2, 4) + ":" + receive.substring(4, 6) + ".000";
			pushQuote(stk, commodityNo, contractNo, dateTimeStamp);
		}
	}

	public String getQuoteCacheKey(String commodityNo, String contractNo) {
		return commodityNo + "-" + contractNo;
	}

	private void pushQuote(StkData stk, String commodityNo, String contractNo, String dateTimeStamp) {
		Message.MessageBase singleQuote = buildSingleQuote(stk, commodityNo, contractNo, dateTimeStamp);
		Message.MessageBase allQuote = buildAllQuote(stk, commodityNo, contractNo, dateTimeStamp);
		Message.MessageBase allFullQuote = buildAllFullQuote(stk, commodityNo, contractNo, dateTimeStamp);
		// 将行情推送到特定的通道中
		for (Map.Entry<String, Channel> entry : ChannelRepository.channelCache.entrySet()) {
			String clientId = entry.getKey();
			Channel channel = entry.getValue();
			// 请求类型
			Attribute<Long> rtinfo = channel.attr(requestTypeInfo);
			if (singleQuote != null && rtinfo.get() != null && rtinfo.get() == 1) {
				// 推送单个行情
				Attribute<String> hyinfo = channel.attr(hyInfo);
				Attribute<String> pzinfo = channel.attr(pzInfo);
				if (channel.isOpen() && hyinfo != null && pzinfo != null && commodityNo.equals(pzinfo.get())
						&& contractNo.equals(hyinfo.get())) {
					singleQuote = Message.MessageBase.newBuilder(singleQuote).setClientId(clientId).build();
					channel.writeAndFlush(singleQuote);
				}
			} else if (rtinfo.get() != null && rtinfo.get() == 2) {
				// 推送全部行情（部分信息）
				allQuote = Message.MessageBase.newBuilder(allQuote).setClientId(clientId).build();
				channel.writeAndFlush(allQuote);
			} else if (rtinfo.get() != null && rtinfo.get() == 3) {
				// 推送全部行情（全信息）
				allFullQuote = Message.MessageBase.newBuilder(allFullQuote).setClientId(clientId).build();
				channel.writeAndFlush(allFullQuote);
			}
		}

		for (Map.Entry<String, Channel> entry : com.waben.stock.futuresgateway.yisheng.server.ws.ChannelRepository.channelCache
				.entrySet()) {
			String clientId = entry.getKey();
			Channel channel = entry.getValue();
			// 请求类型
			Attribute<Long> rtinfo = channel.attr(requestTypeInfo);
			if (singleQuote != null && rtinfo.get() != null && rtinfo.get() == 1) {
				// 推送单个行情
				Attribute<String> hyinfo = channel.attr(hyInfo);
				Attribute<String> pzinfo = channel.attr(pzInfo);
				if (channel.isOpen() && hyinfo != null && pzinfo != null && commodityNo.equals(pzinfo.get())
						&& contractNo.equals(hyinfo.get())) {
					singleQuote = Message.MessageBase.newBuilder(singleQuote).setClientId(clientId).build();
					channel.writeAndFlush(singleQuote);
				}
			} else if (rtinfo.get() != null && rtinfo.get() == 2) {
				// 推送全部行情（部分信息）
				allQuote = Message.MessageBase.newBuilder(allQuote).setClientId(clientId).build();
				channel.writeAndFlush(allQuote);
			} else if (rtinfo.get() != null && rtinfo.get() == 3) {
				// 推送全部行情（全信息）
				allFullQuote = Message.MessageBase.newBuilder(allFullQuote).setClientId(clientId).build();
				channel.writeAndFlush(allFullQuote);
			}
		}
	}

	private Message.MessageBase buildSingleQuote(StkData info, String commodityNo, String contractNo,
			String dateTimeStamp) {
		if (EsEngine.commodityScaleMap.containsKey(commodityNo)) {
			Integer scale = EsEngine.commodityScaleMap.get(commodityNo);
			// 构建行情推送对象
			FuturesQuoteDataBase data = FuturesQuoteDataBase.newBuilder().setCommodityNo(commodityNo)
					.setContractNo(contractNo).setTime(dateTimeStamp.substring(0, dateTimeStamp.length() - 4))
					.setAskPrice(new BigDecimal(info.getAsk()[0]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize(new BigDecimal(info.getAskVol()[0]).longValue())
					.setBidPrice(new BigDecimal(info.getBid()[0]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize(new BigDecimal(info.getBidVol()[0]).longValue())
					.setAskPrice2(new BigDecimal(info.getAsk()[1]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize2(new BigDecimal(info.getAskVol()[1]).longValue())
					.setBidPrice2(new BigDecimal(info.getBid()[1]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize2(new BigDecimal(info.getBidVol()[1]).longValue())
					.setAskPrice3(new BigDecimal(info.getAsk()[2]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize3(new BigDecimal(info.getAskVol()[2]).longValue())
					.setBidPrice3(new BigDecimal(info.getBid()[2]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize3(new BigDecimal(info.getBidVol()[2]).longValue())
					.setAskPrice4(new BigDecimal(info.getAsk()[3]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize4(new BigDecimal(info.getAskVol()[3]).longValue())
					.setBidPrice4(new BigDecimal(info.getBid()[3]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize4(new BigDecimal(info.getBidVol()[3]).longValue())
					.setAskPrice5(new BigDecimal(info.getAsk()[4]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize5(new BigDecimal(info.getAskVol()[4]).longValue())
					.setBidPrice5(new BigDecimal(info.getBid()[4]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize5(new BigDecimal(info.getBidVol()[4]).longValue())

					.setNowClosePrice(new BigDecimal(info.getTClose()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setClosePrice(new BigDecimal(info.getYClose()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setHighPrice(new BigDecimal(info.getHigh()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setLastPrice(new BigDecimal(info.getNew()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setLastSize(new BigDecimal(info.getLastvol()).longValue())
					.setLowPrice(new BigDecimal(info.getLow()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setOpenPrice(new BigDecimal(info.getOpen()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setVolume(new BigDecimal(info.getVolume()).longValue())
					.setTotalVolume(new BigDecimal(info.getAmount()).longValue())
					.setPreSettlePrice(
							new BigDecimal(info.getYSettle()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setTotalQty(new BigDecimal(info.getVolume()).longValue())
					.setPositionQty(new BigDecimal(info.getAmount()).longValue()).setPrePositionQty(0).build();
			Message.MessageBase msg = Message.MessageBase.newBuilder().setCmd(Command.CommandType.PUSH_DATA)
					.setClientId("0").setType(1).setRequestType(1).setFq(data).build();
			return msg;
		} else {
			return null;
		}
	}

	private Message.MessageBase buildAllQuote(StkData info, String commodityNo, String contractNo,
			String dateTimeStamp) {
		Message.MessageBase msg = Message.MessageBase.newBuilder().setCmd(Command.CommandType.PUSH_DATA)
				.setClientId("0").setType(1).setRequestType(2).build();
		if (EsEngine.commodityScaleMap.containsKey(commodityNo)) {
			Integer scale = EsEngine.commodityScaleMap.get(commodityNo);
			FuturesQuoteSimpleDataBase simple = FuturesQuoteSimpleDataBase.newBuilder().setTime(dateTimeStamp)
					.setCommodityNo(commodityNo).setContractNo(contractNo)
					.setAskPrice(new BigDecimal(info.getAsk()[0]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize(new BigDecimal(info.getAskVol()[0]).longValue())
					.setBidPrice(new BigDecimal(info.getBid()[0]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize(new BigDecimal(info.getBidVol()[0]).longValue())
					.setLastPrice(new BigDecimal(info.getNew()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setOpenPrice(new BigDecimal(info.getOpen()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setHighPrice(new BigDecimal(info.getHigh()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setLowPrice(new BigDecimal(info.getLow()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setClosePrice(new BigDecimal(info.getYClose()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setPreSettlePrice(
							new BigDecimal(info.getYSettle()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setTotalQty(new BigDecimal(info.getVolume()).longValue())
					.setPositionQty(new BigDecimal(info.getAmount()).longValue()).setPrePositionQty(0).build();
			msg = Message.MessageBase.newBuilder(msg).addFqList(simple).build();
		}
		return msg;
	}

	private MessageBase buildAllFullQuote(StkData info, String commodityNo, String contractNo, String dateTimeStamp) {
		if (EsEngine.commodityScaleMap.containsKey(commodityNo)) {
			Integer scale = EsEngine.commodityScaleMap.get(commodityNo);
			// 构建行情推送对象
			FuturesQuoteFullDataBase data = FuturesQuoteFullDataBase.newBuilder().setCommodityNo(commodityNo)
					.setContractNo(contractNo).setTime(dateTimeStamp.substring(0, dateTimeStamp.length() - 4))
					.setAskPrice(new BigDecimal(info.getAsk()[0]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize(new BigDecimal(info.getAskVol()[0]).longValue())
					.setBidPrice(new BigDecimal(info.getBid()[0]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize(new BigDecimal(info.getBidVol()[0]).longValue())
					.setAskPrice2(new BigDecimal(info.getAsk()[1]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize2(new BigDecimal(info.getAskVol()[1]).longValue())
					.setBidPrice2(new BigDecimal(info.getBid()[1]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize2(new BigDecimal(info.getBidVol()[1]).longValue())
					.setAskPrice3(new BigDecimal(info.getAsk()[2]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize3(new BigDecimal(info.getAskVol()[2]).longValue())
					.setBidPrice3(new BigDecimal(info.getBid()[2]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize3(new BigDecimal(info.getBidVol()[2]).longValue())
					.setAskPrice4(new BigDecimal(info.getAsk()[3]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize4(new BigDecimal(info.getAskVol()[3]).longValue())
					.setBidPrice4(new BigDecimal(info.getBid()[3]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize4(new BigDecimal(info.getBidVol()[3]).longValue())
					.setAskPrice5(new BigDecimal(info.getAsk()[4]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize5(new BigDecimal(info.getAskVol()[4]).longValue())
					.setBidPrice5(new BigDecimal(info.getBid()[4]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize5(new BigDecimal(info.getBidVol()[4]).longValue())
					.setAskPrice6(new BigDecimal(info.getAsk()[5]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize6(new BigDecimal(info.getAskVol()[5]).longValue())
					.setBidPrice6(new BigDecimal(info.getBid()[5]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize6(new BigDecimal(info.getBidVol()[5]).longValue())
					.setAskPrice7(new BigDecimal(info.getAsk()[6]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize7(new BigDecimal(info.getAskVol()[6]).longValue())
					.setBidPrice7(new BigDecimal(info.getBid()[6]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize7(new BigDecimal(info.getBidVol()[6]).longValue())
					.setAskPrice8(new BigDecimal(info.getAsk()[7]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize8(new BigDecimal(info.getAskVol()[7]).longValue())
					.setBidPrice8(new BigDecimal(info.getBid()[7]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize8(new BigDecimal(info.getBidVol()[7]).longValue())
					.setAskPrice9(new BigDecimal(info.getAsk()[8]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize9(new BigDecimal(info.getAskVol()[8]).longValue())
					.setBidPrice9(new BigDecimal(info.getBid()[8]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize9(new BigDecimal(info.getBidVol()[8]).longValue())
					.setAskPrice10(new BigDecimal(info.getAsk()[9]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setAskSize10(new BigDecimal(info.getAskVol()[9]).longValue())
					.setBidPrice10(new BigDecimal(info.getBid()[9]).setScale(scale, RoundingMode.HALF_UP).toString())
					.setBidSize10(new BigDecimal(info.getBidVol()[9]).longValue())
					.setClosePrice(new BigDecimal(info.getYClose()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setNowClosePrice(new BigDecimal(info.getTClose()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setHighPrice(new BigDecimal(info.getHigh()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setLastPrice(new BigDecimal(info.getNew()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setLastSize(new BigDecimal(info.getLastvol()).longValue())
					.setLowPrice(new BigDecimal(info.getLow()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setOpenPrice(new BigDecimal(info.getOpen()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setVolume(new BigDecimal(info.getVolume()).longValue())
					.setTotalVolume(new BigDecimal(info.getAmount()).longValue())
					.setPreSettlePrice(
							new BigDecimal(info.getYSettle()).setScale(scale, RoundingMode.HALF_UP).toString())
					.setTotalQty(new BigDecimal(info.getVolume()).longValue())
					.setPositionQty(new BigDecimal(info.getAmount()).longValue()).setPrePositionQty(0).build();
			Message.MessageBase msg = Message.MessageBase.newBuilder().setCmd(Command.CommandType.PUSH_DATA)
					.setClientId("0").setType(1).setRequestType(3).setFullFq(data).build();
			return msg;
		} else {
			return null;
		}
	}

	/************************** 以下方法为setter和getter ***********************/

	public QuoteApi getApi() {
		return api;
	}

	public void setApi(QuoteApi api) {
		this.api = api;
	}

	public Map<String, StkData> getQuoteCache() {
		return quoteCache;
	}

	public void setQuoteCache(Map<String, StkData> quoteCache) {
		this.quoteCache = quoteCache;
	}

}
