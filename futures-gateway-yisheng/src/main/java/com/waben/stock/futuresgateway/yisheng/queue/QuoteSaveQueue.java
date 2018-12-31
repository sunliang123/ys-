package com.waben.stock.futuresgateway.yisheng.queue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.future.api.es.external.quote.bean.TapAPIQuoteWhole;
import com.future.api.es.external3.quote.bean.StkData;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuote;
import com.waben.stock.futuresgateway.yisheng.rabbitmq.message.EsQuoteInfo;
import com.waben.stock.futuresgateway.yisheng.service.FuturesQuoteService;
import com.waben.stock.futuresgateway.yisheng.util.JacksonUtil;

// @Service
public class QuoteSaveQueue {

	private BlockingQueue<EsQuoteInfo> quotes = new LinkedBlockingQueue<>();

	Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private FuturesQuoteService quoteService;

	private ExecutorService excutor = Executors.newCachedThreadPool();

	@PostConstruct
	public void init() {
		// 开启开仓处理队列
		new Thread(new QuoteSaveHandler()).start();
	}

	private class QuoteSaveHandler implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					EsQuoteInfo quote = quotes.take();
					SaveRunable job = new SaveRunable(quote);
					excutor.execute(job);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private class SaveRunable implements Runnable {

		private EsQuoteInfo quote;

		public SaveRunable(EsQuoteInfo quote) {
			this.quote = quote;
		}

		@Override
		public void run() {
			try {
				int apiType = quote.getApiType();
				if (apiType == 9) {
					// 9.0行情处理
					TapAPIQuoteWhole msgObj = quote.getInfo9();
					String commodityNo = msgObj.getContract().getCommodity().getCommodityNo();
					String contractNo = msgObj.getContract().getContractNo1();
					char commodityType = msgObj.getContract().getCommodity().getCommodityType();
					if (commodityType == 'F') {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
						// 保存行情信息
						FuturesQuote quote = new FuturesQuote();
						quote.setQuoteIndex(quote.getQuoteIndex());
						quote.setAskPrice(JacksonUtil.encode(msgObj.getQAskPrice()));
						quote.setAskQty(JacksonUtil.encode(msgObj.getQAskQty()));
						quote.setAveragePrice(String.valueOf(msgObj.getQAveragePrice()));
						quote.setBidPrice(JacksonUtil.encode(msgObj.getQBidPrice()));
						quote.setBidQty(JacksonUtil.encode(msgObj.getQBidQty()));
						quote.setChangeRate(String.valueOf(msgObj.getQChangeRate()));
						quote.setChangeSpeed(String.valueOf(msgObj.getQChangeSpeed()));
						quote.setChangeValue(String.valueOf(msgObj.getQChangeValue()));
						quote.setClosingPrice(String.valueOf(msgObj.getQClosingPrice()));
						quote.setCommodityNo(commodityNo);
						quote.setContractNo(contractNo);
						quote.setCurrDelta(String.valueOf(msgObj.getQCurrDelta()));
						quote.setD5AvgQty(msgObj.getQ5DAvgQty());
						quote.setDateTimeStamp(msgObj.getDateTimeStamp());
						quote.setHighPrice(String.valueOf(msgObj.getQHighPrice()));
						quote.setHisHighPrice(String.valueOf(msgObj.getQHisHighPrice()));
						quote.setHisLowPrice(String.valueOf(msgObj.getQHisLowPrice()));
						quote.setImpliedAskPrice(String.valueOf(msgObj.getQImpliedAskPrice()));
						quote.setImpliedAskQty(msgObj.getQImpliedAskQty());
						quote.setImpliedBidPrice(String.valueOf(msgObj.getQImpliedBidPrice()));
						quote.setImpliedBidQty(msgObj.getQImpliedBidQty());
						quote.setInsideQty(msgObj.getQInsideQty());
						quote.setLastPrice(String.valueOf(msgObj.getQLastPrice()));
						quote.setLastQty(msgObj.getQLastQty());
						quote.setLimitDownPrice(String.valueOf(msgObj.getQLimitDownPrice()));
						quote.setLimitUpPrice(String.valueOf(msgObj.getQLimitUpPrice()));
						quote.setLowPrice(String.valueOf(msgObj.getQLowPrice()));
						quote.setNegotiableValue(String.valueOf(msgObj.getQNegotiableValue()));
						quote.setOpeningPrice(String.valueOf(msgObj.getQOpeningPrice()));
						quote.setOutsideQty(msgObj.getQOutsideQty());
						quote.setPeRatio(String.valueOf(msgObj.getQPERatio()));
						quote.setPositionQty(msgObj.getQPositionQty());
						quote.setPositionTrend(msgObj.getQPositionTrend());
						quote.setPreClosingPrice(String.valueOf(msgObj.getQPreClosingPrice()));
						quote.setPreDelta(String.valueOf(msgObj.getQPreDelta()));
						quote.setPrePositionQty(msgObj.getQPrePositionQty());
						quote.setPreSettlePrice(String.valueOf(msgObj.getQPreSettlePrice()));
						quote.setSettlePrice(String.valueOf(msgObj.getQSettlePrice()));
						quote.setSwing(String.valueOf(msgObj.getQSwing()));
						Date nowTime = sdf.parse(msgObj.getDateTimeStamp());
						quote.setTime(nowTime);
						quote.setTotalAskQty(msgObj.getQTotalAskQty());
						quote.setTotalBidQty(msgObj.getQTotalBidQty());
						quote.setTotalQty(msgObj.getQTotalQty());
						quote.setTotalTurnover(String.valueOf(msgObj.getQTotalTurnover()));
						quote.setTotalValue(String.valueOf(msgObj.getQTotalValue()));
						quote.setTurnoverRate(String.valueOf(msgObj.getQTurnoverRate()));
						quoteService.addFuturesQuote(quote);
					}
				} else {
					// 3.0行情处理
					StkData msgObj = quote.getInfo3();
					String[] combine = msgObj.getCode().split(" ");
					String commodityNo = combine[1];
					String contractNo = combine[2];
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
					SimpleDateFormat daySdf = new SimpleDateFormat("yyyy-MM-dd");
					String receive = new BigDecimal(msgObj.getUpdatetime()).setScale(0, RoundingMode.DOWN).toString();
					String dateTimeStamp = daySdf.format(new Date()) + " " + receive.substring(0, 2) + ":"
							+ receive.substring(2, 4) + ":" + receive.substring(4, 6) + ".000";
					// 保存行情信息
					FuturesQuote quote = new FuturesQuote();
					quote.setQuoteIndex(quote.getQuoteIndex());
					quote.setAskPrice(JacksonUtil.encode(msgObj.getAsk()));
					quote.setAskQty(JacksonUtil.encode(msgObj.getAskVol()));
					quote.setAveragePrice(String.valueOf(msgObj.getAvgPrice()));
					quote.setBidPrice(JacksonUtil.encode(msgObj.getBid()));
					quote.setBidQty(JacksonUtil.encode(msgObj.getBidVol()));
					quote.setChangeRate(new BigDecimal(msgObj.getMarkup()).toString());
					quote.setChangeSpeed("0");
					quote.setChangeValue(new BigDecimal(msgObj.getNetChg()).toString());
					quote.setClosingPrice(new BigDecimal(msgObj.getTClose()).toString());
					quote.setCommodityNo(commodityNo);
					quote.setContractNo(contractNo);
					quote.setCurrDelta("0");
					quote.setD5AvgQty(0L);
					quote.setDateTimeStamp(dateTimeStamp);
					quote.setHighPrice(new BigDecimal(msgObj.getHigh()).toString());
					quote.setHisHighPrice(new BigDecimal(msgObj.getHistoryHigh()).toString());
					quote.setHisLowPrice(new BigDecimal(msgObj.getHistoryLow()).toString());
					quote.setImpliedAskPrice("0");
					quote.setImpliedAskQty(0);
					quote.setImpliedBidPrice("0");
					quote.setImpliedBidQty(0);
					quote.setInsideQty(0);
					quote.setLastPrice(new BigDecimal(msgObj.getNew()).toString());
					quote.setLastQty(new BigDecimal(msgObj.getLastvol()).longValue());
					quote.setLimitDownPrice(new BigDecimal(msgObj.getLimitDown()).toString());
					quote.setLimitUpPrice(new BigDecimal(msgObj.getLimitUp()).toString());
					quote.setLowPrice(new BigDecimal(msgObj.getLow()).toString());
					quote.setNegotiableValue("0");
					quote.setOpeningPrice(new BigDecimal(msgObj.getOpen()).toString());
					quote.setOutsideQty(0);
					quote.setPeRatio("0");
					quote.setPositionQty(new BigDecimal(msgObj.getAmount()).longValue());
					quote.setPositionTrend(new BigDecimal(msgObj.getAmount()).longValue());
					quote.setPreClosingPrice(new BigDecimal(msgObj.getYClose()).toString());
					quote.setPreDelta("0");
					quote.setPrePositionQty(0L);
					quote.setPreSettlePrice(new BigDecimal(msgObj.getYSettle()).toString());
					quote.setSettlePrice(new BigDecimal(msgObj.getSettle()).toString());
					quote.setSwing(new BigDecimal(msgObj.getSwing()).toString());
					Date nowTime = sdf.parse(dateTimeStamp);
					quote.setTime(nowTime);
					quote.setTotalAskQty(0L);
					quote.setTotalBidQty(0L);
					quote.setTotalQty(new BigDecimal(msgObj.getVolume()).longValue());
					quote.setTotalTurnover("0");
					quote.setTotalValue("0");
					quote.setTurnoverRate("0");
					quoteService.addFuturesQuote(quote);
				}
			} catch (Exception ex) {
				logger.error("消费易盛Quote消息异常!", ex);
			}
		}
	}

	public void addQuote(EsQuoteInfo quote) {
		this.quotes.add(quote);
	}

}
