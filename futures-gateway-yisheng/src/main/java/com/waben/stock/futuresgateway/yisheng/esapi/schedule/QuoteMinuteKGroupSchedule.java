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
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuoteMinuteKGroup;
import com.waben.stock.futuresgateway.yisheng.entity.MongoFuturesQuoteMinuteK;
import com.waben.stock.futuresgateway.yisheng.service.FuturesContractService;
import com.waben.stock.futuresgateway.yisheng.service.FuturesQuoteMinuteKGroupService;
import com.waben.stock.futuresgateway.yisheng.service.FuturesQuoteMinuteKService;
import com.waben.stock.futuresgateway.yisheng.util.JacksonUtil;

/**
 * 行情-分钟K组合作业
 * 
 * @author lma
 *
 */
@Component
@EnableScheduling
public class QuoteMinuteKGroupSchedule {

	Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private FuturesContractService contractService;

	@Autowired
	private FuturesQuoteMinuteKService minuteKServcie;

	@Autowired
	private FuturesQuoteMinuteKGroupService minuteKGroupServcie;

	/**
	 * 每小时组合上一小时的分钟K，计算小时K
	 */
	@Scheduled(cron = "0 22 0/1 * * ?")
	public void computeMinuteKGroup() {
		// SimpleDateFormat hourSdf = new SimpleDateFormat("yyyy-MM-dd HH:");
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// step 1 : 获取可用的合约
		List<FuturesContract> contractList = contractService.getByEnable(true);
		// step 2 : 获取上一小时
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		Date time = cal.getTime();
		cal.set(Calendar.MINUTE, 1);
		cal.add(Calendar.HOUR_OF_DAY, -1);
		Date beforeTime = cal.getTime();
		cal.add(Calendar.HOUR_OF_DAY, 1);
		Date afterTime = cal.getTime();
		// step 3 : 遍历所有合约，计算小时K
		for (FuturesContract contract : contractList) {
			String commodityNo = contract.getCommodityNo();
			String contractNo = contract.getContractNo();
			try {
				// step 3.1 : 判断之前是否有计算过
				FuturesQuoteMinuteKGroup minuteKGroup = minuteKGroupServcie
						.getByCommodityNoAndContractNoAndTime(commodityNo, contractNo, time);
				if (minuteKGroup != null) {
					continue;
				}
				// step 3.2 : 根据时间获取上一小时的分钟K
				List<MongoFuturesQuoteMinuteK> minuteKList = minuteKServcie
						.retrieveByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo,
								contractNo, beforeTime, afterTime);
				if (minuteKList != null && minuteKList.size() > 0) {
					// step 3.3 : 初始化部分数据
					minuteKGroup = new FuturesQuoteMinuteKGroup();
					minuteKGroup.setCommodityNo(commodityNo);
					minuteKGroup.setContractNo(contractNo);
					minuteKGroup.setTime(time);
					minuteKGroup.setTimeStr(fullSdf.format(time));
					minuteKGroup.setTotalVolume(minuteKList.get(minuteKList.size() - 1).getTotalVolume());
					Long startTotalQty = minuteKList.get(0).getStartTotalQty();
					Long endTotalQty = minuteKList.get(minuteKList.size() - 1).getEndTotalQty();
					minuteKGroup.setEndTotalQty(endTotalQty);
					minuteKGroup.setStartTotalQty(startTotalQty);
					if (endTotalQty != null && startTotalQty != null) {
						minuteKGroup.setVolume(endTotalQty - startTotalQty);
					} else {
						minuteKGroup.setVolume(0L);
					}
					minuteKGroup.setOpenPrice(minuteKList.get(0).getOpenPrice());
					minuteKGroup.setClosePrice(minuteKList.get(minuteKList.size() - 1).getClosePrice());
					// step 3.4 : 计算最高价、最低价
					BigDecimal highPrice = minuteKList.get(0).getHighPrice();
					BigDecimal lowPrice = minuteKList.get(0).getLowPrice();
					for (MongoFuturesQuoteMinuteK minuteK : minuteKList) {
						if (minuteK.getHighPrice().compareTo(highPrice) > 0) {
							highPrice = minuteK.getHighPrice();
						}
						if (minuteK.getLowPrice().compareTo(lowPrice) < 0) {
							lowPrice = minuteK.getLowPrice();
						}
					}
					minuteKGroup.setHighPrice(highPrice);
					minuteKGroup.setLowPrice(lowPrice);
					minuteKGroup.setGroupData(JacksonUtil.encode(minuteKList));
					// step 3.5 : 保存计算出来的分K数据
					minuteKGroupServcie.addFuturesQuoteMinuteKGroup(minuteKGroup);
					// step 3.6 : 删除分K的行情数据
					// for (MongoFuturesQuoteMinuteK minuteK : minuteKList) {
					// EsDeleteQuoteMessage delQuote = new
					// EsDeleteQuoteMessage();
					// delQuote.setQuoteId(String.valueOf(minuteK.getId()));
					// delQuote.setType(2);
					// producer.sendMessage(RabbitmqConfiguration.deleteQuoteQueueName,
					// delQuote);
					// }
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				logger.error("计算{}-{}小时K异常!", commodityNo, contractNo);
			}
		}
		logger.info("计算分K组合数据结束:" + fullSdf.format(new Date()));
	}

}
