package com.waben.stock.futuresgateway.yisheng.esapi.schedule;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.waben.stock.futuresgateway.yisheng.entity.FuturesContract;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuote;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuoteLast;
import com.waben.stock.futuresgateway.yisheng.entity.MongoFuturesQuoteMinuteK;
import com.waben.stock.futuresgateway.yisheng.rabbitmq.RabbitmqConfiguration;
import com.waben.stock.futuresgateway.yisheng.rabbitmq.RabbitmqProducer;
import com.waben.stock.futuresgateway.yisheng.rabbitmq.message.EsDeleteQuoteMessage;
import com.waben.stock.futuresgateway.yisheng.service.FuturesContractService;
import com.waben.stock.futuresgateway.yisheng.service.FuturesQuoteMinuteKService;
import com.waben.stock.futuresgateway.yisheng.service.FuturesQuoteService;
import com.waben.stock.futuresgateway.yisheng.util.CopyBeanUtils;

/**
 * 行情分钟作业
 * 
 * @author lma
 *
 */
@Component
@EnableScheduling
public class QuoteMinuteKSchedule {

	Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private FuturesContractService contractService;

	@Autowired
	private FuturesQuoteService quoteService;

	@Autowired
	private FuturesQuoteMinuteKService minuteKServcie;

	@Autowired
	private RabbitmqProducer producer;

	/**
	 * 每分钟计算上一分钟的分钟K
	 */
	@Scheduled(cron = "30 0/1 * * * ?")
	public void computeMinuteK() {
		SimpleDateFormat minSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:");
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm:ss");
		// step 1 : 获取可用的合约
		List<FuturesContract> contractList = contractService.getByEnable(true);
		// step 2 : 获取上一分钟
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.add(Calendar.MINUTE, -1);
		Date before = cal.getTime();
		cal.add(Calendar.MINUTE, 1);
		Date currentMin = cal.getTime();
		// step 3 : 遍历所有合约，计算分钟K
		for (FuturesContract contract : contractList) {
			String commodityNo = contract.getCommodityNo();
			String contractNo = contract.getContractNo();
			String currentMinStr = timeSdf.format(currentMin);
			if (("MHI".equals(commodityNo) || "HSI".equals(commodityNo)) && currentMinStr.compareTo("12:01:00") >= 0
					&& currentMinStr.compareTo("13:00:00") <= 0) {
				continue;
			}
			// step 3.1 : 判断之前是否有计算过
			MongoFuturesQuoteMinuteK beforeMinuteK = minuteKServcie.getByCommodityNoAndContractNoAndTime(commodityNo,
					contractNo, currentMin);
			if (beforeMinuteK != null) {
				continue;
			}
			// step 3.2 : 根据时间获取所有的行情
			String dateTimeStamp = minSdf.format(before);
			FuturesQuote first = quoteService.miniteFirst(commodityNo, contractNo, dateTimeStamp);
			FuturesQuote last = quoteService.miniteLast(commodityNo, contractNo, dateTimeStamp);
			FuturesQuote max = quoteService.minuteMax(commodityNo, contractNo, dateTimeStamp);
			FuturesQuote min = quoteService.minuteMin(commodityNo, contractNo, dateTimeStamp);
			BigDecimal openPrice = getPrice(first);
			BigDecimal closePrice = getPrice(last);
			BigDecimal highPrice = getPrice(max);
			BigDecimal lowPrice = getPrice(min);
			// step 3.5 : 保存计算出来的分K数据
			if (openPrice != null && openPrice.compareTo(BigDecimal.ZERO) > 0 && closePrice != null
					&& closePrice.compareTo(BigDecimal.ZERO) > 0 && highPrice != null
					&& highPrice.compareTo(BigDecimal.ZERO) > 0 && lowPrice != null
					&& lowPrice.compareTo(BigDecimal.ZERO) > 0) {
				// step 3.3 : 初始化部分数据
				beforeMinuteK = new MongoFuturesQuoteMinuteK();
				beforeMinuteK.setCommodityNo(commodityNo);
				beforeMinuteK.setContractNo(contractNo);
				beforeMinuteK.setTime(currentMin);
				beforeMinuteK.setTimeStr(fullSdf.format(currentMin));
				beforeMinuteK.setTotalVolume(last.getPositionQty());
				MongoFuturesQuoteMinuteK beforeBeforeMinuteK = minuteKServcie
						.getByCommodityNoAndContractNoAndTime(commodityNo, contractNo, before);
				if (beforeBeforeMinuteK != null) {
					beforeMinuteK.setStartTotalQty(beforeBeforeMinuteK.getEndTotalQty());
				} else {
					beforeMinuteK.setStartTotalQty(first.getTotalQty());
				}
				beforeMinuteK.setEndTotalQty(last.getTotalQty());
				beforeMinuteK.setVolume(beforeMinuteK.getEndTotalQty() - beforeMinuteK.getStartTotalQty());
				beforeMinuteK.setOpenPrice(openPrice);
				beforeMinuteK.setClosePrice(closePrice);
				beforeMinuteK.setHighPrice(highPrice);
				beforeMinuteK.setLowPrice(lowPrice);
				minuteKServcie.addFuturesQuoteMinuteK(beforeMinuteK);
				// step 3.6 : 删除该分钟的行情数据
				quoteService.minuteAllQuoteDel(commodityNo, contractNo, dateTimeStamp);
				// step 3.7 : 保存当前分钟的最后一条数据
				last.setId(null);
				quoteService.addFuturesQuote(last);
			}
		}
		logger.info("计算分K数据结束:" + fullSdf.format(new Date()));
	}

	public void computeMinuteK_last() {
		SimpleDateFormat minSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:");
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm:ss");
		// step 1 : 获取可用的合约
		List<FuturesContract> contractList = contractService.getByEnable(true);
		// step 2 : 获取上一分钟
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.add(Calendar.MINUTE, -1);
		Date before = cal.getTime();
		cal.add(Calendar.MINUTE, 1);
		Date currentMin = cal.getTime();
		// step 3 : 遍历所有合约，计算分钟K
		for (FuturesContract contract : contractList) {
			String commodityNo = contract.getCommodityNo();
			String contractNo = contract.getContractNo();
			String currentMinStr = timeSdf.format(currentMin);
			if (("MHI".equals(commodityNo) || "HSI".equals(commodityNo)) && currentMinStr.compareTo("12:01:00") >= 0
					&& currentMinStr.compareTo("13:00:00") <= 0) {
				continue;
			}
			// step 3.1 : 判断之前是否有计算过
			MongoFuturesQuoteMinuteK beforeMinuteK = minuteKServcie.getByCommodityNoAndContractNoAndTime(commodityNo,
					contractNo, currentMin);
			if (beforeMinuteK != null) {
				continue;
			}
			// step 3.2 : 根据时间获取所有的行情
			List<FuturesQuote> quoteList = quoteService.getByCommodityNoAndContractNoAndDateTimeStampLike(commodityNo,
					contractNo, minSdf.format(before));
			if (quoteList != null && quoteList.size() > 0) {
				// step 3.3 : 初始化部分数据
				beforeMinuteK = new MongoFuturesQuoteMinuteK();
				beforeMinuteK.setCommodityNo(commodityNo);
				beforeMinuteK.setContractNo(contractNo);
				beforeMinuteK.setTime(currentMin);
				beforeMinuteK.setTimeStr(fullSdf.format(currentMin));
				beforeMinuteK.setTotalVolume(quoteList.get(quoteList.size() - 1).getPositionQty());
				MongoFuturesQuoteMinuteK beforeBeforeMinuteK = minuteKServcie
						.getByCommodityNoAndContractNoAndTime(commodityNo, contractNo, before);
				if (beforeBeforeMinuteK != null) {
					beforeMinuteK.setStartTotalQty(beforeBeforeMinuteK.getEndTotalQty());
				} else {
					beforeMinuteK.setStartTotalQty(quoteList.get(0).getTotalQty());
				}
				beforeMinuteK.setEndTotalQty(quoteList.get(quoteList.size() - 1).getTotalQty());
				beforeMinuteK.setVolume(beforeMinuteK.getEndTotalQty() - beforeMinuteK.getStartTotalQty());
				BigDecimal openPrice = new BigDecimal(quoteList.get(0).getLastPrice());
				beforeMinuteK.setOpenPrice(openPrice);
				BigDecimal closePrice = new BigDecimal(quoteList.get(quoteList.size() - 1).getLastPrice());
				beforeMinuteK.setClosePrice(closePrice);
				// step 3.4 : 计算最高价、最低价
				BigDecimal highPrice = new BigDecimal(quoteList.get(0).getLastPrice());
				BigDecimal lowPrice = new BigDecimal(quoteList.get(0).getLastPrice());
				for (FuturesQuote quote : quoteList) {
					if (new BigDecimal(quote.getLastPrice()).compareTo(highPrice) > 0) {
						highPrice = new BigDecimal(quote.getLastPrice());
					}
					if (new BigDecimal(quote.getLastPrice()).compareTo(lowPrice) < 0) {
						lowPrice = new BigDecimal(quote.getLastPrice());
					}
				}
				beforeMinuteK.setHighPrice(highPrice);
				beforeMinuteK.setLowPrice(lowPrice);
				// step 3.5 : 保存计算出来的分K数据
				if (openPrice != null && openPrice.compareTo(BigDecimal.ZERO) > 0 && closePrice != null
						&& closePrice.compareTo(BigDecimal.ZERO) > 0 && highPrice != null
						&& highPrice.compareTo(BigDecimal.ZERO) > 0 && lowPrice != null
						&& lowPrice.compareTo(BigDecimal.ZERO) > 0) {
					minuteKServcie.addFuturesQuoteMinuteK(beforeMinuteK);
					// step 3.6 : 删除该分钟的行情数据
					for (int i = 0; i < quoteList.size(); i++) {
						if (i == quoteList.size() - 1) {
							continue;
						}
						FuturesQuote quote = quoteList.get(i);
						EsDeleteQuoteMessage delQuote = new EsDeleteQuoteMessage();
						delQuote.setCommodityNo(commodityNo);
						delQuote.setContractNo(contractNo);
						delQuote.setQuoteId(quote.getId());
						delQuote.setType(1);
						producer.sendMessage(RabbitmqConfiguration.deleteQuoteQueueName, delQuote);
					}
				}
			}
		}
		logger.info("计算分K数据结束:" + fullSdf.format(new Date()));
	}

	private BigDecimal getPrice(FuturesQuote quote) {
		if (quote != null) {
			return new BigDecimal(quote.getLastPrice());
		}
		return null;
	}

	@SuppressWarnings("unused")
	private FuturesQuoteLast convertToQuoteLast(FuturesQuote quote) {
		FuturesQuoteLast result = CopyBeanUtils.copyBeanProperties(FuturesQuoteLast.class, quote, false);
		result.setPreClosingPrice(
				quote.getPreClosingPrice() != null ? new BigDecimal(quote.getPreClosingPrice()) : null);
		result.setPreSettlePrice(quote.getPreSettlePrice() != null ? new BigDecimal(quote.getPreSettlePrice()) : null);
		result.setOpeningPrice(quote.getOpeningPrice() != null ? new BigDecimal(quote.getOpeningPrice()) : null);
		result.setLastPrice(quote.getLastPrice() != null ? new BigDecimal(quote.getLastPrice()) : null);
		result.setHighPrice(quote.getHighPrice() != null ? new BigDecimal(quote.getHighPrice()) : null);
		result.setLowPrice(quote.getLowPrice() != null ? new BigDecimal(quote.getLowPrice()) : null);
		result.setHisHighPrice(quote.getHisHighPrice() != null ? new BigDecimal(quote.getHisHighPrice()) : null);
		result.setHisLowPrice(quote.getHisLowPrice() != null ? new BigDecimal(quote.getHisLowPrice()) : null);
		result.setLimitUpPrice(quote.getLimitUpPrice() != null ? new BigDecimal(quote.getLimitUpPrice()) : null);
		result.setLimitDownPrice(quote.getLimitDownPrice() != null ? new BigDecimal(quote.getLimitDownPrice()) : null);
		result.setTotalTurnover(quote.getTotalTurnover() != null ? new BigDecimal(quote.getTotalTurnover()) : null);
		result.setAveragePrice(quote.getAveragePrice() != null ? new BigDecimal(quote.getAveragePrice()) : null);
		result.setClosingPrice(quote.getClosingPrice() != null ? new BigDecimal(quote.getClosingPrice()) : null);
		result.setSettlePrice(quote.getSettlePrice() != null ? new BigDecimal(quote.getSettlePrice()) : null);
		result.setImpliedBidPrice(
				quote.getImpliedBidPrice() != null ? new BigDecimal(quote.getImpliedBidPrice()) : null);
		result.setImpliedAskPrice(
				quote.getImpliedAskPrice() != null ? new BigDecimal(quote.getImpliedAskPrice()) : null);
		result.setPreDelta(quote.getPreDelta() != null ? new BigDecimal(quote.getPreDelta()) : null);
		result.setCurrDelta(quote.getCurrDelta() != null ? new BigDecimal(quote.getCurrDelta()) : null);
		result.setTurnoverRate(quote.getTurnoverRate() != null ? new BigDecimal(quote.getTurnoverRate()) : null);
		result.setPeRatio(quote.getPeRatio() != null ? new BigDecimal(quote.getPeRatio()) : null);
		result.setTotalValue(quote.getTotalValue() != null ? new BigDecimal(quote.getTotalValue()) : null);
		result.setNegotiableValue(
				quote.getNegotiableValue() != null ? new BigDecimal(quote.getNegotiableValue()) : null);
		result.setChangeSpeed(quote.getChangeSpeed() != null ? new BigDecimal(quote.getChangeSpeed()) : null);
		result.setChangeRate(quote.getChangeRate() != null ? new BigDecimal(quote.getChangeRate()) : null);
		result.setChangeValue(quote.getChangeValue() != null ? new BigDecimal(quote.getChangeValue()) : null);
		result.setSwing(quote.getSwing() != null ? new BigDecimal(quote.getSwing()) : null);
		return result;
	}

	@SuppressWarnings("unused")
	private boolean isExchangeSameDay(Date d1, Date d2, int timeZoneGap) {
		Calendar c1 = Calendar.getInstance();
		c1.setTime(d1);
		c1.add(Calendar.HOUR_OF_DAY, timeZoneGap * -1);
		Date exchangeD1 = c1.getTime();

		Calendar c2 = Calendar.getInstance();
		c2.setTime(d2);
		c2.add(Calendar.HOUR_OF_DAY, timeZoneGap * -1);
		Date exchangeD2 = c2.getTime();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		return sdf.format(exchangeD1).equals(sdf.format(exchangeD2));
	}

}
