package com.waben.stock.futuresgateway.yisheng.esapi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.future.api.es.external.common.bean.TapAPICommodity;
import com.future.api.es.external.common.bean.TapAPIContract;
import com.future.api.es.external.common.constants.Constants;
import com.future.api.es.external.quote.bean.TapAPIQuoteWhole;
import com.future.api.es.external.trade.bean.TapAPINewOrder;
import com.future.api.es.external.trade.bean.TapAPIOrderCancelReq;
import com.future.api.es.external3.quote.bean.StkData;
import com.waben.stock.futuresgateway.yisheng.dao.FuturesCommodityDao;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesCommodity;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesContract;
import com.waben.stock.futuresgateway.yisheng.esapi.esapi3.Es3QuoteWrapper;
import com.waben.stock.futuresgateway.yisheng.esapi.esapi9.Es9QuoteWrapper;
import com.waben.stock.futuresgateway.yisheng.esapi.esapi9.Es9TradeWrapper;
import com.waben.stock.futuresgateway.yisheng.exception.ExceptionEnum;
import com.waben.stock.futuresgateway.yisheng.exception.ServiceException;
import com.waben.stock.futuresgateway.yisheng.pojo.FuturesQuoteData;

@Component
public class EsEngine {

	@Value("${es.api.type}")
	private int esApiType;

	@Autowired
	private Es3QuoteWrapper quote3Wrapper;

	@Autowired
	private Es9QuoteWrapper quote9Wrapper;

	@Autowired
	private Es9TradeWrapper trade9Wrapper;

	@Autowired
	private FuturesCommodityDao commodifyDao;

	public static Map<String, String> testCommodityExchangeMap = new HashMap<String, String>();
	static {
		testCommodityExchangeMap.put("BP", "CME");
		testCommodityExchangeMap.put("CD", "CME");
		testCommodityExchangeMap.put("NQ", "CME");
		testCommodityExchangeMap.put("GC", "COMEX");
		testCommodityExchangeMap.put("HG", "COMEX");
		testCommodityExchangeMap.put("SI", "COMEX");
		testCommodityExchangeMap.put("HSI", "HKEX");
		testCommodityExchangeMap.put("MHI", "HKEX");
		testCommodityExchangeMap.put("CL", "NYMEX");
		testCommodityExchangeMap.put("CN", "SGX");
	}

	public static Map<String, Integer> commodityScaleMap = new HashMap<String, Integer>();

	@PostConstruct
	public void initCommodityScale() {
		List<FuturesCommodity> commodityList = commodifyDao.listFuturesCommodity();
		for (FuturesCommodity commodity : commodityList) {
			if (commodity.getCommodityTickSize() != null && "F".equals(commodity.getCommodityType())) {
				commodityScaleMap.put(commodity.getCommodityNo(), getScale(commodity.getCommodityTickSize()));
			}
		}
		// 连接行情
		if (esApiType == 3) {
			quote3Wrapper.init();
		} else if (esApiType == 9) {
			quote9Wrapper.init();
		}
	}

	public int qryContract(TapAPICommodity commodity) {
		if (commodity != null) {
			return quote9Wrapper.getApi().qryContract(commodity);
		} else {
			return 0;
		}
	}

	public int subscribeQuote(TapAPIContract contract) {
		if (contract != null) {
			return quote9Wrapper.getApi().subscribeQuote(contract);
		} else {
			return 0;
		}
	}

	public String getAccount() {
		return trade9Wrapper.getTradeUsername();
	}

	public int placeOrder(FuturesCommodity commodity, FuturesContract contract, String action, BigDecimal totalQuantity,
			Integer orderType, BigDecimal entrustPrice) {
		TapAPINewOrder order = new TapAPINewOrder();
		order.setAccountNo(trade9Wrapper.getTradeUsername());
		// TODO 测试账号先使用纽交所
		if (testCommodityExchangeMap.containsKey(commodity.getCommodityNo())) {
			order.setExchangeNo(testCommodityExchangeMap.get(commodity.getCommodityNo()));
		} else {
			order.setExchangeNo(commodity.getExchangeNo());
		}
		order.setCommodityNo(commodity.getCommodityNo());
		order.setCommodityType(commodity.getCommodityType().charAt(0));
		order.setContractNo(contract.getContractNo());
		if (orderType != null && orderType == 1) {
			order.setOrderType(Constants.TAPI_ORDER_TYPE_MARKET);
		} else if (orderType != null && orderType == 2) {
			order.setOrderType(Constants.TAPI_ORDER_TYPE_LIMIT);
			order.setOrderPrice(entrustPrice.doubleValue());
		} else {
			throw new ServiceException(ExceptionEnum.OrderType_NotSupported);
		}
		order.setOrderQty(totalQuantity.intValue());
		if (!("BUY".equals(action) || "SELL".equals(action))) {
			throw new ServiceException(ExceptionEnum.Action_NotSupported);
		}
		if ("BUY".equals(action)) {
			order.setOrderSide(Constants.TAPI_SIDE_BUY);
		} else {
			order.setOrderSide(Constants.TAPI_SIDE_SELL);
		}
		order.setTimeInForce(Constants.TAPI_ORDER_TIMEINFORCE_GTC);
		return trade9Wrapper.getApi().insertOrder(order);
	}
	
	public String getQuoteCacheKey(String commodityNo, String contractNo) {
		return commodityNo + "-" + contractNo;
	}

	public FuturesQuoteData convertToQuoteData(String commodityNo, String contractNo) throws ParseException {
		FuturesQuoteData data = null;
		Integer scale = EsEngine.commodityScaleMap.get(commodityNo);
		if (scale != null) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			if (esApiType == 3) {
				String cacheKey = quote3Wrapper.getQuoteCacheKey(commodityNo, contractNo);
				StkData info = quote3Wrapper.getQuoteCache().get(cacheKey);
				if (info != null) {
					data = new FuturesQuoteData();
					SimpleDateFormat daySdf = new SimpleDateFormat("yyyy-MM-dd");
					String receive = new BigDecimal(info.getUpdatetime()).setScale(0, RoundingMode.DOWN).toString();
					String dateTimeStamp = daySdf.format(new Date()) + " " + receive.substring(0, 2) + ":"
							+ receive.substring(2, 4) + ":" + receive.substring(4, 6) + ".000";
					data.setCommodityNo(commodityNo);
					data.setContractNo(contractNo);
					if (dateTimeStamp != null) {
						data.setTime(sdf.parse(dateTimeStamp.substring(0, dateTimeStamp.length() - 4)));
					}
					data.setAskPrice(new BigDecimal(info.getAsk()[0]).setScale(scale, RoundingMode.HALF_UP));
					data.setAskSize(new BigDecimal(info.getAskVol()[0]).longValue());
					data.setBidPrice(new BigDecimal(info.getBid()[0]).setScale(scale, RoundingMode.HALF_UP));
					data.setBidSize(new BigDecimal(info.getBidVol()[0]).longValue());
					if (info.getAsk().length > 1) {
						// 卖2~卖10
						data.setAskPrice2(new BigDecimal(info.getAsk()[1]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice3(new BigDecimal(info.getAsk()[2]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice4(new BigDecimal(info.getAsk()[3]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice5(new BigDecimal(info.getAsk()[4]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice6(new BigDecimal(info.getAsk()[5]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice7(new BigDecimal(info.getAsk()[6]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice8(new BigDecimal(info.getAsk()[7]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice9(new BigDecimal(info.getAsk()[8]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice10(new BigDecimal(info.getAsk()[9]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskSize2(new BigDecimal(info.getAskVol()[1]).longValue());
						data.setAskSize3(new BigDecimal(info.getAskVol()[2]).longValue());
						data.setAskSize4(new BigDecimal(info.getAskVol()[3]).longValue());
						data.setAskSize5(new BigDecimal(info.getAskVol()[4]).longValue());
						data.setAskSize6(new BigDecimal(info.getAskVol()[5]).longValue());
						data.setAskSize7(new BigDecimal(info.getAskVol()[6]).longValue());
						data.setAskSize8(new BigDecimal(info.getAskVol()[7]).longValue());
						data.setAskSize9(new BigDecimal(info.getAskVol()[8]).longValue());
						data.setAskSize10(new BigDecimal(info.getAskVol()[9]).longValue());
						// 买2~买10
						data.setBidPrice2(new BigDecimal(info.getBid()[1]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice3(new BigDecimal(info.getBid()[2]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice4(new BigDecimal(info.getBid()[3]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice5(new BigDecimal(info.getBid()[4]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice6(new BigDecimal(info.getBid()[5]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice7(new BigDecimal(info.getBid()[6]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice8(new BigDecimal(info.getBid()[7]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice9(new BigDecimal(info.getBid()[8]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice10(new BigDecimal(info.getBid()[9]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidSize2(new BigDecimal(info.getBidVol()[1]).longValue());
						data.setBidSize3(new BigDecimal(info.getBidVol()[2]).longValue());
						data.setBidSize4(new BigDecimal(info.getBidVol()[3]).longValue());
						data.setBidSize5(new BigDecimal(info.getBidVol()[4]).longValue());
						data.setBidSize6(new BigDecimal(info.getBidVol()[5]).longValue());
						data.setBidSize7(new BigDecimal(info.getBidVol()[6]).longValue());
						data.setBidSize8(new BigDecimal(info.getBidVol()[7]).longValue());
						data.setBidSize9(new BigDecimal(info.getBidVol()[8]).longValue());
						data.setBidSize10(new BigDecimal(info.getBidVol()[9]).longValue());
					}
					data.setNowClosePrice(new BigDecimal(info.getTClose()).setScale(scale, RoundingMode.HALF_UP));
					data.setClosePrice(new BigDecimal(info.getYClose()).setScale(scale, RoundingMode.HALF_UP));
					data.setHighPrice(new BigDecimal(info.getHigh()).setScale(scale, RoundingMode.HALF_UP));
					data.setLastPrice(new BigDecimal(info.getNew()).setScale(scale, RoundingMode.HALF_UP));
					data.setLastSize(new BigDecimal(info.getLastvol()).longValue());
					data.setLowPrice(new BigDecimal(info.getLow()).setScale(scale, RoundingMode.HALF_UP));
					data.setOpenPrice(new BigDecimal(info.getOpen()).setScale(scale, RoundingMode.HALF_UP));
					data.setVolume(new BigDecimal(info.getVolume()).longValue());
					data.setTotalVolume(new BigDecimal(info.getAmount()).longValue());
					data.setPreSettlePrice(new BigDecimal(info.getYSettle()).setScale(scale, RoundingMode.HALF_UP));
					data.setTotalQty(new BigDecimal(info.getVolume()).longValue());
					data.setPositionQty(new BigDecimal(info.getAmount()).longValue());
					data.setPrePositionQty(0L);
				}
			} else if (esApiType == 9) {
				String cacheKey = quote9Wrapper.getQuoteCacheKey(commodityNo, contractNo);
				TapAPIQuoteWhole info = quote9Wrapper.getQuoteCache().get(cacheKey);
				if (info != null) {
					data = new FuturesQuoteData();
					data.setCommodityNo(commodityNo);
					data.setContractNo(contractNo);
					if (info.getDateTimeStamp() != null) {
						data.setTime(
								sdf.parse(info.getDateTimeStamp().substring(0, info.getDateTimeStamp().length() - 4)));
					}
					data.setAskPrice(new BigDecimal(info.getQAskPrice()[0]).setScale(scale, RoundingMode.HALF_UP));
					data.setAskSize(info.getQAskQty()[0]);
					data.setBidPrice(new BigDecimal(info.getQBidPrice()[0]).setScale(scale, RoundingMode.HALF_UP));
					data.setBidSize(info.getQBidQty()[0]);
					if (info.getQAskPrice().length > 1) {
						// 卖2~卖10
						data.setAskPrice2(new BigDecimal(info.getQAskPrice()[1]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice3(new BigDecimal(info.getQAskPrice()[2]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice4(new BigDecimal(info.getQAskPrice()[3]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice5(new BigDecimal(info.getQAskPrice()[4]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice6(new BigDecimal(info.getQAskPrice()[5]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice7(new BigDecimal(info.getQAskPrice()[6]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice8(new BigDecimal(info.getQAskPrice()[7]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice9(new BigDecimal(info.getQAskPrice()[8]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskPrice10(
								new BigDecimal(info.getQAskPrice()[9]).setScale(scale, RoundingMode.HALF_UP));
						data.setAskSize2(info.getQAskQty()[1]);
						data.setAskSize3(info.getQAskQty()[2]);
						data.setAskSize4(info.getQAskQty()[3]);
						data.setAskSize5(info.getQAskQty()[4]);
						data.setAskSize6(info.getQAskQty()[5]);
						data.setAskSize7(info.getQAskQty()[6]);
						data.setAskSize8(info.getQAskQty()[7]);
						data.setAskSize9(info.getQAskQty()[8]);
						data.setAskSize10(info.getQAskQty()[9]);
						// 买2~买10
						data.setBidPrice2(new BigDecimal(info.getQBidPrice()[1]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice3(new BigDecimal(info.getQBidPrice()[2]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice4(new BigDecimal(info.getQBidPrice()[3]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice5(new BigDecimal(info.getQBidPrice()[4]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice6(new BigDecimal(info.getQBidPrice()[5]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice7(new BigDecimal(info.getQBidPrice()[6]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice8(new BigDecimal(info.getQBidPrice()[7]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice9(new BigDecimal(info.getQBidPrice()[8]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidPrice10(
								new BigDecimal(info.getQBidPrice()[9]).setScale(scale, RoundingMode.HALF_UP));
						data.setBidSize2(info.getQBidQty()[1]);
						data.setBidSize3(info.getQBidQty()[2]);
						data.setBidSize4(info.getQBidQty()[3]);
						data.setBidSize5(info.getQBidQty()[4]);
						data.setBidSize6(info.getQBidQty()[5]);
						data.setBidSize7(info.getQBidQty()[6]);
						data.setBidSize8(info.getQBidQty()[7]);
						data.setBidSize9(info.getQBidQty()[8]);
						data.setBidSize10(info.getQBidQty()[9]);
					}
					data.setNowClosePrice(
							new BigDecimal(info.getQClosingPrice()).setScale(scale, RoundingMode.HALF_UP));
					data.setClosePrice(
							new BigDecimal(info.getQPreClosingPrice()).setScale(scale, RoundingMode.HALF_UP));
					data.setHighPrice(new BigDecimal(info.getQHighPrice()).setScale(scale, RoundingMode.HALF_UP));
					data.setLastPrice(new BigDecimal(info.getQLastPrice()).setScale(scale, RoundingMode.HALF_UP));
					data.setLastSize(info.getQLastQty());
					data.setLowPrice(new BigDecimal(info.getQLowPrice()).setScale(scale, RoundingMode.HALF_UP));
					data.setOpenPrice(new BigDecimal(info.getQOpeningPrice()).setScale(scale, RoundingMode.HALF_UP));
					data.setVolume(info.getQTotalQty());
					data.setTotalVolume(info.getQPositionQty());
					data.setPreSettlePrice(
							new BigDecimal(info.getQPreSettlePrice()).setScale(scale, RoundingMode.HALF_UP));
					data.setTotalQty(info.getQTotalQty());
					data.setPositionQty(info.getQPositionQty());
					data.setPrePositionQty(info.getQPrePositionQty());
				}
			}
		}
		return data;
	}

	public int cancelOrder(String orderNo) {
		TapAPIOrderCancelReq req = new TapAPIOrderCancelReq();
		req.setOrderNo(orderNo);
		return trade9Wrapper.getApi().cancelOrder(req);
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

}
