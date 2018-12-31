package com.waben.stock.futuresgateway.yisheng.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.future.api.es.external.common.bean.TapAPICommodity;
import com.future.api.es.external.common.bean.TapAPIContract;
import com.future.api.es.external.quote.bean.TapAPIQuoteWhole;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesCommodityDao;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesContractDao;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesQuoteDao;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesQuoteDayKDao;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesQuoteLastDao;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesQuoteMinuteKDao;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesCommodity;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesContract;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuote;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuoteDayK;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuoteLast;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuoteMinuteKGroup;
import com.waben.stock.futuresgateway.yisheng.entity.MongoFuturesQuoteMinuteK;
import com.waben.stock.futuresgateway.yisheng.esapi.EsEngine;
import com.waben.stock.futuresgateway.yisheng.esapi.esapi3.Es3QuoteWrapper;
import com.waben.stock.futuresgateway.yisheng.esapi.esapi9.Es9QuoteWrapper;
import com.waben.stock.futuresgateway.yisheng.pojo.FuturesContractLineData;
import com.waben.stock.futuresgateway.yisheng.pojo.FuturesQuoteData;
import com.waben.stock.futuresgateway.yisheng.util.CopyBeanUtils;
import com.waben.stock.futuresgateway.yisheng.util.JacksonUtil;
import com.waben.stock.futuresgateway.yisheng.util.StringUtil;
import com.waben.stock.futuresgateway.yisheng.util.TimeZoneUtil;

/**
 * 期货行情 Service
 * 
 * @author lma
 *
 */
@Service
public class FuturesMarketService {

	Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private FuturesCommodityDao commodityDao;

	@Autowired
	private FuturesContractDao contractDao;

	@Autowired
	private FuturesQuoteDao quoteDao;

	@Autowired
	private FuturesQuoteLastDao quoteLastDao;

	@Autowired
	private FuturesQuoteMinuteKDao minuteKDao;

	@Autowired
	private FuturesQuoteDayKDao dayKDao;

	@Autowired
	private FuturesQuoteDayKService dayKServcie;

	@Autowired
	private FuturesQuoteMinuteKGroupService minuteKGroupServcie;

	@Autowired
	private Es3QuoteWrapper quote3Wrapper;

	@Autowired
	private Es9QuoteWrapper quote9Wrapper;

	@Autowired
	private EsEngine esEngine;

	@Value("${es.api.type}")
	private int esApiType;

	private double toDouble(BigDecimal bd) {
		return bd != null ? bd.doubleValue() : 0;
	}
	
	private long toLong(Long bd) {
		return bd != null ? bd.longValue() : 0;
	}
	
	@PostConstruct
	public void initQuoteCache() {
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Map<String, TapAPIQuoteWhole> quoteCache = quote9Wrapper.getQuoteCache();
		List<FuturesContract> contractList = contractDao.retriveByEnable(true);
		for (FuturesContract contract : contractList) {
			FuturesQuoteData quote = quote(contract.getCommodityNo(), contract.getContractNo());
			if (quote.getLastPrice().compareTo(BigDecimal.ZERO) > 0) {
				TapAPIQuoteWhole quoteWhole = new TapAPIQuoteWhole();
				quoteWhole.setQAskPrice(new double[] { toDouble(quote.getAskPrice()), toDouble(quote.getAskPrice2()), toDouble(quote.getAskPrice3()), toDouble(quote.getAskPrice4()), toDouble(quote.getAskPrice5()), toDouble(quote.getAskPrice6()), toDouble(quote.getAskPrice7()), toDouble(quote.getAskPrice8()), toDouble(quote.getAskPrice9()), toDouble(quote.getAskPrice10()) });
				quoteWhole.setQAskQty(new long[] { toLong(quote.getAskSize()),toLong(quote.getAskSize2()),toLong(quote.getAskSize3()),toLong(quote.getAskSize4()),toLong(quote.getAskSize5()),toLong(quote.getAskSize6()),toLong(quote.getAskSize7()),toLong(quote.getAskSize8()),toLong(quote.getAskSize9()),toLong(quote.getAskSize10()) });
				quoteWhole.setQBidPrice(new double[] { toDouble(quote.getBidPrice()),toDouble(quote.getBidPrice2()),toDouble(quote.getBidPrice3()),toDouble(quote.getBidPrice4()),toDouble(quote.getBidPrice5()),toDouble(quote.getBidPrice6()),toDouble(quote.getBidPrice7()),toDouble(quote.getBidPrice8()),toDouble(quote.getBidPrice9()),toDouble(quote.getBidPrice10()) });
				quoteWhole.setQBidQty(new long[] { toLong(quote.getBidSize()),toLong(quote.getBidSize2()),toLong(quote.getBidSize3()),toLong(quote.getBidSize4()),toLong(quote.getBidSize5()),toLong(quote.getBidSize6()),toLong(quote.getBidSize7()),toLong(quote.getBidSize8()),toLong(quote.getBidSize9()),toLong(quote.getBidSize10()) });
				if (quote.getTime() != null) {
					quoteWhole.setDateTimeStamp(fullSdf.format(quote.getTime()) + ".000");
				}
				quoteWhole.setQLastPrice(quote.getLastPrice().doubleValue());
				quoteWhole.setQLastQty(quote.getLastSize().longValue());
				if (quote.getNowClosePrice() != null) {
					quoteWhole.setQClosingPrice(quote.getNowClosePrice().doubleValue());
				}
				quoteWhole.setQPreClosingPrice(quote.getClosePrice().doubleValue());
				quoteWhole.setQHighPrice(quote.getHighPrice().doubleValue());
				quoteWhole.setQLowPrice(quote.getLowPrice().doubleValue());
				quoteWhole.setQOpeningPrice(quote.getOpenPrice().doubleValue());
				quoteWhole.setQTotalQty(quote.getTotalVolume());
				TapAPIContract apiContract = new TapAPIContract();
				apiContract.setContractNo1(quote.getContractNo());
				TapAPICommodity apiCommodity = new TapAPICommodity();
				apiCommodity.setCommodityNo(quote.getCommodityNo());
				apiContract.setCommodity(apiCommodity);
				quoteWhole.setContract(apiContract);

				quoteCache.put(quote9Wrapper.getQuoteCacheKey(quote.getCommodityNo(), quote.getContractNo()),
						quoteWhole);
			}
		}
	}

	public FuturesQuoteData quote(String commodityNo, String contractNo) {
		FuturesQuoteData result = new FuturesQuoteData();
		result.setCommodityNo(commodityNo);
		result.setContractNo(contractNo);
		// 查询品种
		FuturesCommodity commodity = commodityDao.retrieveByCommodityNo(commodityNo);
		if (commodity != null && commodity.getCommodityTickSize() != null) {
			Integer scale = getScale(commodity.getCommodityTickSize());
			// 查询行情
			FuturesQuote quote = quoteDao.retriveNewest(commodityNo, contractNo);
			if (quote != null) {
				List<BigDecimal> askPriceList = JacksonUtil.decode(quote.getAskPrice(),
						JacksonUtil.getGenericType(List.class, BigDecimal.class));
				List<Long> askSizeList = JacksonUtil.decode(quote.getAskQty(),
						JacksonUtil.getGenericType(List.class, Long.class));
				List<BigDecimal> bidPriceList = JacksonUtil.decode(quote.getBidPrice(),
						JacksonUtil.getGenericType(List.class, BigDecimal.class));
				List<Long> bidSizeList = JacksonUtil.decode(quote.getBidQty(),
						JacksonUtil.getGenericType(List.class, Long.class));
				result.setTime(quote.getTime());
				// 卖1~卖10
				result.setAskPrice(askPriceList.get(0).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice2(askPriceList.get(1).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice3(askPriceList.get(2).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice4(askPriceList.get(3).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice5(askPriceList.get(4).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice6(askPriceList.get(5).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice7(askPriceList.get(6).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice8(askPriceList.get(7).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice9(askPriceList.get(8).setScale(scale, RoundingMode.HALF_UP));
				result.setAskPrice10(askPriceList.get(9).setScale(scale, RoundingMode.HALF_UP));
				result.setAskSize(askSizeList.get(0));
				result.setAskSize2(askSizeList.get(1));
				result.setAskSize3(askSizeList.get(2));
				result.setAskSize4(askSizeList.get(3));
				result.setAskSize5(askSizeList.get(4));
				result.setAskSize6(askSizeList.get(5));
				result.setAskSize7(askSizeList.get(6));
				result.setAskSize8(askSizeList.get(7));
				result.setAskSize9(askSizeList.get(8));
				result.setAskSize10(askSizeList.get(9));
				// 买1~买10
				result.setBidPrice(bidPriceList.get(0).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice2(bidPriceList.get(1).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice3(bidPriceList.get(2).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice4(bidPriceList.get(3).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice5(bidPriceList.get(4).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice6(bidPriceList.get(5).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice7(bidPriceList.get(6).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice8(bidPriceList.get(7).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice9(bidPriceList.get(8).setScale(scale, RoundingMode.HALF_UP));
				result.setBidPrice10(bidPriceList.get(9).setScale(scale, RoundingMode.HALF_UP));
				result.setBidSize(bidSizeList.get(0));
				result.setBidSize2(bidSizeList.get(1));
				result.setBidSize3(bidSizeList.get(2));
				result.setBidSize4(bidSizeList.get(3));
				result.setBidSize5(bidSizeList.get(4));
				result.setBidSize6(bidSizeList.get(5));
				result.setBidSize7(bidSizeList.get(6));
				result.setBidSize8(bidSizeList.get(7));
				result.setBidSize9(bidSizeList.get(8));
				result.setBidSize10(bidSizeList.get(9));
				result.setClosePrice(new BigDecimal(quote.getPreClosingPrice()).setScale(scale, RoundingMode.HALF_UP));
				result.setNowClosePrice(new BigDecimal(quote.getClosingPrice()).setScale(scale, RoundingMode.HALF_UP));
				result.setHighPrice(new BigDecimal(quote.getHighPrice()).setScale(scale, RoundingMode.HALF_UP));
				result.setLastPrice(new BigDecimal(quote.getLastPrice()).setScale(scale, RoundingMode.HALF_UP));
				result.setLastSize(quote.getLastQty());
				result.setLowPrice(new BigDecimal(quote.getLowPrice()).setScale(scale, RoundingMode.HALF_UP));
				result.setOpenPrice(new BigDecimal(quote.getOpeningPrice()).setScale(scale, RoundingMode.HALF_UP));
				result.setVolume(quote.getTotalQty());
				result.setTotalVolume(quote.getPositionQty());
				result.setPreSettlePrice(
						new BigDecimal(quote.getPreSettlePrice()).setScale(scale, RoundingMode.HALF_UP));
				result.setTotalQty(quote.getTotalQty());
				result.setPositionQty(quote.getPositionQty());
				result.setPrePositionQty(quote.getPrePositionQty());
				return result;
			}
			// 行情中没有查询到，查新行情-最新
			FuturesQuoteLast quoteLast = quoteLastDao.retriveNewest(commodityNo, contractNo);
			if (quoteLast != null) {
				List<BigDecimal> askPriceList = JacksonUtil.decode(quoteLast.getAskPrice(),
						JacksonUtil.getGenericType(List.class, BigDecimal.class));
				List<Long> askSizeList = JacksonUtil.decode(quoteLast.getAskQty(),
						JacksonUtil.getGenericType(List.class, Long.class));
				List<BigDecimal> bidPriceList = JacksonUtil.decode(quoteLast.getBidPrice(),
						JacksonUtil.getGenericType(List.class, BigDecimal.class));
				List<Long> bidSizeList = JacksonUtil.decode(quoteLast.getBidQty(),
						JacksonUtil.getGenericType(List.class, Long.class));
				result.setAskPrice(askPriceList.get(0).setScale(scale, RoundingMode.HALF_UP));
				result.setAskSize(askSizeList.get(0));
				result.setBidPrice(bidPriceList.get(0).setScale(scale, RoundingMode.HALF_UP));
				result.setBidSize(bidSizeList.get(0));
				result.setClosePrice(quoteLast.getClosingPrice());
				result.setHighPrice(quoteLast.getHighPrice());
				result.setLastPrice(quoteLast.getLastPrice());
				result.setLastSize(quoteLast.getLastQty());
				result.setLowPrice(quoteLast.getLowPrice());
				result.setOpenPrice(quoteLast.getOpeningPrice());
				result.setVolume(quoteLast.getTotalQty());
				result.setTotalVolume(quoteLast.getPositionQty());
				result.setPreSettlePrice(quoteLast.getPreSettlePrice().setScale(scale, RoundingMode.HALF_UP));
				result.setTotalQty(quoteLast.getTotalQty());
				result.setPositionQty(quoteLast.getPositionQty());
				result.setPrePositionQty(quoteLast.getPrePositionQty());
				return result;
			}
		}
		// 未查询到最新行情，全部初始化值为0
		result.setAskPrice(BigDecimal.ZERO);
		result.setAskSize(0L);
		result.setBidSize(0L);
		result.setBidPrice(BigDecimal.ZERO);
		
		result.setAskPrice2(BigDecimal.ZERO);
		result.setAskSize2(0L);
		result.setBidSize2(0L);
		result.setBidPrice2(BigDecimal.ZERO);
		
		result.setAskPrice3(BigDecimal.ZERO);
		result.setAskSize3(0L);
		result.setBidSize3(0L);
		result.setBidPrice2(BigDecimal.ZERO);
		
		result.setAskPrice4(BigDecimal.ZERO);
		result.setAskSize4(0L);
		result.setBidSize4(0L);
		result.setBidPrice4(BigDecimal.ZERO);
		
		result.setAskPrice5(BigDecimal.ZERO);
		result.setAskSize5(0L);
		result.setBidSize5(0L);
		result.setBidPrice5(BigDecimal.ZERO);
		
		result.setAskPrice6(BigDecimal.ZERO);
		result.setAskSize6(0L);
		result.setBidSize6(0L);
		result.setBidPrice6(BigDecimal.ZERO);
		
		result.setAskPrice7(BigDecimal.ZERO);
		result.setAskSize7(0L);
		result.setBidSize7(0L);
		result.setBidPrice7(BigDecimal.ZERO);
		
		result.setAskPrice8(BigDecimal.ZERO);
		result.setAskSize8(0L);
		result.setBidSize8(0L);
		result.setBidPrice8(BigDecimal.ZERO);
		
		result.setAskPrice9(BigDecimal.ZERO);
		result.setAskSize9(0L);
		result.setBidSize9(0L);
		result.setBidPrice9(BigDecimal.ZERO);
		
		result.setAskPrice10(BigDecimal.ZERO);
		result.setAskSize10(0L);
		result.setBidSize10(0L);
		result.setBidPrice10(BigDecimal.ZERO);
		
		result.setClosePrice(BigDecimal.ZERO);
		result.setHighPrice(BigDecimal.ZERO);
		result.setLastPrice(BigDecimal.ZERO);
		result.setLastSize(0L);
		result.setLowPrice(BigDecimal.ZERO);
		result.setOpenPrice(BigDecimal.ZERO);
		result.setNowClosePrice(BigDecimal.ZERO);
		result.setTotalVolume(0L);
		result.setVolume(0L);
		result.setPreSettlePrice(BigDecimal.ZERO);
		result.setTotalQty(0L);
		result.setPositionQty(0L);
		result.setPrePositionQty(0L);
		return result;
	}

	public List<FuturesContractLineData> dayLine(String commodityNo, String contractNo, String startTimeStr,
			String endTimeStr) {
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		// 获取开始和结束时间
		Date startTime = null;
		Date betweenTime = null;
		Date endTime = null;
		try {
			if (!StringUtil.isEmpty(startTimeStr)) {
				startTime = fullSdf.parse(startTimeStr);
			}
			if (!StringUtil.isEmpty(endTimeStr)) {
				endTime = fullSdf.parse(endTimeStr);
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		boolean isNeedAddToday = false;
		if (endTime == null) {
			endTime = new Date();
			isNeedAddToday = true;
		}
		if (startTime == null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(endTime);
			cal.add(Calendar.YEAR, -1);
			startTime = cal.getTime();
		}

		FuturesContract contract = contractDao.retrieveByCommodityNoAndContractNo(commodityNo, contractNo);
		if (contract == null) {
			return new ArrayList<>();
		} else {
			if (contract.getDayKMainContractEndTime() != null
					&& contract.getDayKMainContractEndTime().getTime() > startTime.getTime()) {
				betweenTime = contract.getDayKMainContractEndTime();
			} else {
				betweenTime = startTime;
			}
		}

		// 获取主力合约的日K数据
		List<FuturesQuoteDayK> mainDayKList = dayKDao
				.retrieveByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo, "main",
						startTime, betweenTime);
		if (mainDayKList == null) {
			mainDayKList = new ArrayList<>();
		}
		// 获取日K数据
		List<FuturesQuoteDayK> dayKList = dayKDao
				.retrieveByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo, contractNo,
						betweenTime, endTime);
		if (dayKList == null) {
			dayKList = new ArrayList<>();
		}
		mainDayKList.addAll(dayKList);

		if (isNeedAddToday) {
			FuturesQuoteDayK last = null;
			if (mainDayKList != null && mainDayKList.size() > 0) {
				last = mainDayKList.get(mainDayKList.size() - 1);
			}
			try {
				FuturesQuoteData quote = esEngine.convertToQuoteData(commodityNo, contractNo);
				if (quote != null && sdf.format(quote.getTime()).equals(sdf.format(new Date()))) {
					if (last == null || sdf.format(quote.getTime())
							.compareTo(last.getTimeStr().substring(0, last.getTimeStr().length() - 9)) > 0) {
						if (quote.getNowClosePrice().compareTo(BigDecimal.ZERO) > 0
								&& quote.getHighPrice().compareTo(BigDecimal.ZERO) > 0
								&& quote.getLowPrice().compareTo(BigDecimal.ZERO) > 0
								&& quote.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
							Integer scale = EsEngine.commodityScaleMap.get(commodityNo);
							if (scale != null) {
								FuturesQuoteDayK add = new FuturesQuoteDayK();
								add.setClosePrice(quote.getLastPrice());
								add.setCommodityNo(commodityNo);
								add.setContractNo(contractNo);
								add.setEndTotalQty(quote.getTotalQty());
								add.setHighPrice(quote.getHighPrice());
								add.setLowPrice(quote.getLowPrice());
								add.setOpenPrice(quote.getOpenPrice());
								try {
									add.setTime(sdf.parse(sdf.format(quote.getTime())));
									add.setTimeStr(sdf.format(quote.getTime()));
									add.setVolume(quote.getTotalQty());
									add.setTotalVolume(quote.getPositionQty());
									mainDayKList.add(add);
								} catch (ParseException e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
			} catch (ParseException e1) {
				logger.error("行情日期格式有误!");
				e1.printStackTrace();
			}
		}
		return CopyBeanUtils.copyListBeanPropertiesToList(mainDayKList, FuturesContractLineData.class);
	}

	public List<FuturesContractLineData> hoursLine(String commodityNo, String contractNo, String startTimeStr,
			String endTimeStr, int hours) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// 获取开始和结束时间
		Date startTime = null;
		Date betweenTime = null;
		Date endTime = null;
		try {
			if (!StringUtil.isEmpty(startTimeStr)) {
				startTime = fullSdf.parse(startTimeStr);
			}
			if (!StringUtil.isEmpty(endTimeStr)) {
				endTime = fullSdf.parse(endTimeStr);
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if (endTime == null) {
			endTime = new Date();
		}
		if (startTime == null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(endTime);
			int weekDay = cal.get(Calendar.DAY_OF_WEEK);
			if (weekDay == 7) {
				// 星期六
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 1);
			} else if (weekDay == 1) {
				// 星期日
				cal.add(Calendar.DAY_OF_MONTH, -1);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 1);
			} else if (weekDay == 2 && fullSdf.format(endTime)
					.compareTo(sdf.format(endTime) + " " + TimeZoneUtil.getOpenTime(commodityNo)) < 0) {
				// 星期一
				cal.add(Calendar.DAY_OF_MONTH, -2);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 1);
			}
			Date computeTime = cal.getTime();
			Date[] timeArr = TimeZoneUtil.retriveBeijingTimeInterval(computeTime, commodityNo);
			startTime = timeArr[0];
		}

		// 判断是否需要获取主力合约的历史数据
		FuturesContract contract = contractDao.retrieveByCommodityNoAndContractNo(commodityNo, contractNo);
		if (contract == null) {
			return new ArrayList<>();
		} else {
			if (contract.getMinuteKMainContractEndTime() != null
					&& contract.getMinuteKMainContractEndTime().getTime() > startTime.getTime()) {
				betweenTime = contract.getMinuteKMainContractEndTime();
			} else {
				betweenTime = startTime;
			}
		}
		// 获取主力合约的分K数据（main）
		List<MongoFuturesQuoteMinuteK> minuteKList = minuteKDao
				.retrieveByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo, "main",
						startTime, betweenTime);
		if (minuteKList == null) {
			minuteKList = new ArrayList<>();
		}
		// 查询分时数据（constractNo）
		List<MongoFuturesQuoteMinuteK> realMinuteKList = minuteKDao
				.retrieveByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo, contractNo,
						betweenTime, endTime);
		minuteKList.addAll(realMinuteKList);
		// 统计
		List<FuturesContractLineData> result = new ArrayList<>();
		Integer scale = EsEngine.commodityScaleMap.get(commodityNo);
		for (MongoFuturesQuoteMinuteK minuteK : minuteKList) {
			if (minuteK.getOpenPrice() != null && minuteK.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
				FuturesContractLineData lineData = CopyBeanUtils.copyBeanProperties(FuturesContractLineData.class,
						minuteK, false);
				lineData.setScale(scale);
				lineData.setMins(hours * 60);
				result.add(lineData);
			}
		}
		// 排序
		Collections.sort(result);
		if (hours > 1) {
			List<FuturesContractLineData> minsResult = new ArrayList<>();
			for (int i = 0; i < result.size(); i++) {
				FuturesContractLineData data = result.get(i);
				Date date = data.getTime();
				Calendar cal = Calendar.getInstance();
				cal.setTime(date);
				int min = cal.get(Calendar.MINUTE);
				int hour = cal.get(Calendar.HOUR_OF_DAY);
				if (min == 0 && hour % hours == 0) {
					List<FuturesContractLineData> computeList = new ArrayList<>();
					BigDecimal highPrice = data.getHighPrice();
					BigDecimal lowPrice = data.getLowPrice();
					Long volume = 0L;
					for (int j = i; j > i - hours * 60; j--) {
						if (j >= 0 && date.getTime() - result.get(j).getTime().getTime() < hours * 60 * 60 * 1000) {
							FuturesContractLineData jData = result.get(j);
							if (jData.getHighPrice().compareTo(highPrice) > 0) {
								highPrice = jData.getHighPrice();
							}
							if (jData.getLowPrice().compareTo(BigDecimal.ZERO) > 0
									&& jData.getLowPrice().compareTo(lowPrice) < 0) {
								lowPrice = jData.getLowPrice();
							}
							volume += jData.getVolume();
							computeList.add(jData);
						}
					}
					FuturesContractLineData compute = new FuturesContractLineData();
					compute.setClosePrice(computeList.get(0).getClosePrice());
					compute.setCommodityNo(data.getCommodityNo());
					compute.setContractNo(data.getContractNo());
					compute.setHighPrice(highPrice);
					compute.setLowPrice(lowPrice);
					compute.setOpenPrice(computeList.get(computeList.size() - 1).getOpenPrice());
					compute.setTime(data.getTime());
					compute.setTimeStr(data.getTimeStr());
					compute.setTotalVolume(data.getTotalVolume());
					compute.setVolume(volume);
					compute.setMins(hours * 60);
					minsResult.add(compute);
				}
			}
			return minsResult;
		} else {
			return result;
		}
	}

	public List<FuturesContractLineData> minsLine(String commodityNo, String contractNo, String startTimeStr,
			String endTimeStr, Integer mins) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// 获取开始和结束时间
		Date startTime = null;
		Date betweenTime = null;
		Date endTime = null;
		try {
			if (!StringUtil.isEmpty(startTimeStr)) {
				startTime = fullSdf.parse(startTimeStr);
			}
			if (!StringUtil.isEmpty(endTimeStr)) {
				endTime = fullSdf.parse(endTimeStr);
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if (endTime == null) {
			endTime = new Date();
		}
		if (startTime == null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(endTime);
			int weekDay = cal.get(Calendar.DAY_OF_WEEK);
			if (weekDay == 7) {
				// 星期六
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 1);
			} else if (weekDay == 1) {
				// 星期日
				cal.add(Calendar.DAY_OF_MONTH, -1);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 1);
			} else if (weekDay == 2 && fullSdf.format(endTime)
					.compareTo(sdf.format(endTime) + " " + TimeZoneUtil.getOpenTime(commodityNo)) < 0) {
				// 星期一
				cal.add(Calendar.DAY_OF_MONTH, -2);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 1);
			}
			Date computeTime = cal.getTime();
			Date[] timeArr = TimeZoneUtil.retriveBeijingTimeInterval(computeTime, commodityNo);
			startTime = timeArr[0];
		}

		// 判断是否需要获取主力合约的历史数据
		FuturesContract contract = contractDao.retrieveByCommodityNoAndContractNo(commodityNo, contractNo);
		if (contract == null) {
			return new ArrayList<>();
		} else {
			if (contract.getMinuteKMainContractEndTime() != null
					&& contract.getMinuteKMainContractEndTime().getTime() > startTime.getTime()) {
				betweenTime = contract.getMinuteKMainContractEndTime();
			} else {
				betweenTime = startTime;
			}
		}
		// 获取主力合约的分K数据（main）
		List<MongoFuturesQuoteMinuteK> minuteKList = minuteKDao
				.retrieveByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo, "main",
						startTime, betweenTime);
		if (minuteKList == null) {
			minuteKList = new ArrayList<>();
		}
		// 查询分时数据（constractNo）
		List<MongoFuturesQuoteMinuteK> realMinuteKList = minuteKDao
				.retrieveByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo, contractNo,
						betweenTime, endTime);
		minuteKList.addAll(realMinuteKList);
		// 统计
		List<FuturesContractLineData> result = new ArrayList<>();
		Integer scale = EsEngine.commodityScaleMap.get(commodityNo);
		for (MongoFuturesQuoteMinuteK minuteK : minuteKList) {
			if (minuteK.getOpenPrice() != null && minuteK.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
				FuturesContractLineData lineData = CopyBeanUtils.copyBeanProperties(FuturesContractLineData.class,
						minuteK, false);
				lineData.setScale(scale);
				lineData.setMins(mins);
				result.add(lineData);
			}
		}
		// 排序
		Collections.sort(result);
		if (mins > 1) {
			List<FuturesContractLineData> minsResult = new ArrayList<>();
			for (int i = 0; i < result.size(); i++) {
				FuturesContractLineData data = result.get(i);
				Date date = data.getTime();
				Calendar cal = Calendar.getInstance();
				cal.setTime(date);
				int min = cal.get(Calendar.MINUTE);
				if (min % mins == 0) {
					List<FuturesContractLineData> computeList = new ArrayList<>();
					BigDecimal highPrice = data.getHighPrice();
					BigDecimal lowPrice = data.getLowPrice();
					Long volume = 0L;
					for (int j = i; j > i - mins; j--) {
						if (j >= 0 && date.getTime() - result.get(j).getTime().getTime() < mins * 60 * 1000) {
							FuturesContractLineData jData = result.get(j);
							if (jData.getHighPrice().compareTo(highPrice) > 0) {
								highPrice = jData.getHighPrice();
							}
							if (jData.getLowPrice().compareTo(BigDecimal.ZERO) > 0
									&& jData.getLowPrice().compareTo(lowPrice) < 0) {
								lowPrice = jData.getLowPrice();
							}
							volume += jData.getVolume();
							computeList.add(jData);
						}
					}
					FuturesContractLineData compute = new FuturesContractLineData();
					compute.setClosePrice(computeList.get(0).getClosePrice());
					compute.setCommodityNo(data.getCommodityNo());
					compute.setContractNo(data.getContractNo());
					compute.setHighPrice(highPrice);
					compute.setLowPrice(lowPrice);
					compute.setOpenPrice(computeList.get(computeList.size() - 1).getOpenPrice());
					compute.setTime(data.getTime());
					compute.setTimeStr(data.getTimeStr());
					compute.setTotalVolume(data.getTotalVolume());
					compute.setVolume(volume);
					compute.setMins(mins);
					minsResult.add(compute);
				}
			}
			return minsResult;
		} else {
			return result;
		}
	}

	private int getScale(BigDecimal num) {
		StringBuilder numStrBuilder = new StringBuilder(num.toString());
		while (true) {
			char last = numStrBuilder.charAt(numStrBuilder.length() - 1);
			if (last == 48) {
				numStrBuilder.deleteCharAt(numStrBuilder.length() - 1);
			} else {
				break;
			}
		}
		return new BigDecimal(numStrBuilder.toString()).scale();
	}

	public void computeDayline(String commodityNo, String contractNo, Date time) {
		innerComputeDayK(commodityNo, contractNo, time);
	}

	private void innerComputeDayK(String commodityNo, String contractNo, Date date) {
		SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date[] arr = retriveBeijingTimeInterval(date);
		List<FuturesQuoteMinuteKGroup> groupList = minuteKGroupServcie
				.getByCommodityNoAndContractNoAndTimeGreaterThanEqualAndTimeLessThan(commodityNo, contractNo, arr[0],
						arr[1]);
		if (groupList != null && groupList.size() > 0) {
			FuturesQuoteDayK dayK = dayKServcie.getByCommodityNoAndContractNoAndTime(commodityNo, contractNo, date);
			if (dayK == null) {
				dayK = new FuturesQuoteDayK();
			}
			// 初始化部分数据
			dayK.setCommodityNo(commodityNo);
			dayK.setContractNo(contractNo);
			dayK.setTime(date);
			dayK.setTimeStr(fullSdf.format(date));
			dayK.setTotalVolume(groupList.get(groupList.size() - 1).getTotalVolume());
			dayK.setVolume(groupList.get(groupList.size() - 1).getTotalVolume());
			dayK.setOpenPrice(groupList.get(0).getOpenPrice());
			dayK.setClosePrice(groupList.get(groupList.size() - 1).getClosePrice());
			// 计算最高价、最低价
			BigDecimal highPrice = groupList.get(0).getHighPrice();
			BigDecimal lowPrice = groupList.get(0).getLowPrice();
			for (FuturesQuoteMinuteKGroup group : groupList) {
				if (group.getHighPrice().compareTo(highPrice) > 0) {
					highPrice = group.getHighPrice();
				}
				if (group.getLowPrice().compareTo(lowPrice) < 0) {
					lowPrice = group.getLowPrice();
				}
			}
			dayK.setHighPrice(highPrice);
			dayK.setLowPrice(lowPrice);
			// 保存计算出来的日K数据
			dayKServcie.addFuturesQuoteDayK(dayK);

		}
	}

	private Date[] retriveBeijingTimeInterval(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.HOUR_OF_DAY, 5);
		cal.add(Calendar.MINUTE, 1);
		Date endTime = cal.getTime();

		Calendar startCal = Calendar.getInstance();
		startCal.setTime(date);
		startCal.add(Calendar.HOUR_OF_DAY, -18);
		startCal.add(Calendar.MINUTE, 1);
		Date startTime = startCal.getTime();

		return new Date[] { startTime, endTime };
	}

	public Map<String, FuturesQuoteData> quoteAll() {
		Map<String, FuturesQuoteData> result = new HashMap<>();
		List<FuturesContract> contractList = contractDao.retriveByEnable(true);
		if (contractList != null && contractList.size() > 0) {
			for (FuturesContract contract : contractList) {
				String commodityNo = contract.getCommodityNo();
				String contractNo = contract.getContractNo();
				try {
					FuturesQuoteData data = esEngine.convertToQuoteData(commodityNo, contractNo);
					if (data != null) {
						String key = esEngine.getQuoteCacheKey(commodityNo, contractNo);
						result.put(key, data);
					}
				} catch (ParseException e) {
					logger.error("行情日期格式有误!");
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	public void subcribe(String commodityNo, String contractNo) {
		FuturesCommodity commodityEntity = commodityDao.retrieveByCommodityNo(commodityNo);
		FuturesContract contractEntity = contractDao.retrieveByCommodityNoAndContractNo(commodityNo, contractNo);
		if (commodityEntity != null && "F".equals(commodityEntity.getCommodityType()) && contractEntity != null) {
			if (esApiType == 3) {
				quote3Wrapper.subscribeQuote(contractEntity.getExchangeNo(), commodityNo, contractNo);
			} else if (esApiType == 9) {
				TapAPIContract contract = new TapAPIContract(
						new TapAPICommodity(commodityEntity.getExchangeNo(), 'F', commodityEntity.getCommodityNo()),
						contractEntity.getContractNo(), null, 'N', null, null, 'N');
				quote9Wrapper.getApi().subscribeQuote(contract);
			}
		}
	}

	public void unsubcribe(String commodityNo, String contractNo) {
		FuturesCommodity commodityEntity = commodityDao.retrieveByCommodityNo(commodityNo);
		FuturesContract contractEntity = contractDao.retrieveByCommodityNoAndContractNo(commodityNo, contractNo);
		if (commodityEntity != null && "F".equals(commodityEntity.getCommodityType()) && contractEntity != null) {
			if (esApiType == 3) {
				quote3Wrapper.unsubscribeQuote(contractEntity.getExchangeNo(), commodityNo, contractNo);
			} else if (esApiType == 9) {
				TapAPIContract contract = new TapAPIContract(
						new TapAPICommodity(commodityEntity.getExchangeNo(), 'F', commodityEntity.getCommodityNo()),
						contractEntity.getContractNo(), null, 'N', null, null, 'N');
				quote9Wrapper.getApi().unSubscribeQuote(contract);
			}
		}
	}

	public void doQuoteReconnect() {
		if (esApiType == 3) {
			try {
				quote3Wrapper.reconnect();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		} else if (esApiType == 9) {
			try {
				quote9Wrapper.reconnect(true);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void repairQuote(FuturesQuote quote) {
		quoteDao.createFuturesQuote(quote);
	}

}
