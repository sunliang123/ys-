package com.waben.stock.futuresgateway.yisheng.esapi.esapi9;

import org.springframework.beans.factory.annotation.Autowired;

import com.future.api.es.external.quote.bean.TapAPIQuoteWhole;
import com.future.api.es.external.util.JacksonUtil;

// @Service
public class TestQuote {
	
	@Autowired
	private Es9QuoteWrapper quoteWrapper;

	// @PostConstruct
	public void init() {
		final String quoteJosn = "{\"qpreClosingPrice\":1231.1,\"qpreSettlePrice\":1231.1,\"qprePositionQty\":0,\"qimpliedAskPrice\":0.0,\"qimpliedAskQty\":0,\"qimpliedBidPrice\":0.0,\"qimpliedBidQty\":0,\"qlimitDownPrice\":1141.1,\"qnegotiableValue\":0.0,\"qpositionTrend\":371988,\"qtotalTurnover\":0.0,\"qaveragePrice\":0.0,\"qchangeRate\":0.11371943790107147,\"qchangeSpeed\":0.0,\"qchangeValue\":1.400000000000091,\"qcurrDelta\":0.0,\"q5DAvgQty\":0,\"dateTimeStamp\":\"2018-10-26 01:17:08.118\",\"qhisHighPrice\":0.0,\"qhisLowPrice\":0.0,\"qinsideQty\":0,\"qlimitUpPrice\":1321.1,\"qoutsideQty\":0,\"qperatio\":0.0,\"qpreDelta\":0.0,\"qsettlePrice\":1231.1,\"qswing\":0.9178783202014608,\"qtotalAskQty\":0,\"qtotalBidQty\":0,\"qtotalValue\":0.0,\"qturnoverRate\":0.0,\"qaskPrice\":[1232.6,1232.6999999999998,1232.8,1232.8999999999999,1233.0,1233.1,1233.1999999999998,1233.3,1233.3999999999999,1233.5,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0],\"qaskQty\":[22,24,34,31,45,31,95,15,21,14,0,0,0,0,0,0,0,0,0,0],\"qbidPrice\":[1232.5,1232.3999999999999,1232.3,1232.1999999999998,1232.1,1232.0,1231.8999999999999,1231.8,1231.6999999999998,1231.6,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0],\"qbidQty\":[11,41,37,35,33,37,20,25,25,28,0,0,0,0,0,0,0,0,0,0],\"qlastPrice\":1232.5,\"qlastQty\":3,\"qclosingPrice\":1231.1,\"qhighPrice\":1242.0,\"qlowPrice\":1230.6999999999998,\"qopeningPrice\":1236.6,\"qtotalQty\":226919,\"contract\":{\"contractNo1\":\"1812\",\"commodity\":{\"commodityNo\":\"GC\",\"exchangeNo\":\"COMEX\",\"commodityType\":\"F\"},\"callOrPutFlag1\":\"N\",\"callOrPutFlag2\":\"N\",\"strikePrice2\":\"\",\"strikePrice1\":\"\",\"contractNo2\":\"\"},\"qpositionQty\":371988,\"underlyContract\":{\"contractNo1\":\"1812\",\"commodity\":{\"commodityNo\":\"GC\",\"exchangeNo\":\"COMEX\",\"commodityType\":\"F\"},\"callOrPutFlag1\":\"N\",\"callOrPutFlag2\":\"N\",\"strikePrice2\":\"\",\"strikePrice1\":\"\",\"contractNo2\":\"\"},\"currencyNo\":\"\",\"tradingState\":\"\u0000\"}";
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				while(true) {
					TapAPIQuoteWhole quote = JacksonUtil.decode(quoteJosn, TapAPIQuoteWhole.class);
					quoteWrapper.onRtnQuote(quote);
				}
			}
		}).start();
	}

}
