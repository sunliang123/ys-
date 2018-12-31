package com.waben.stock.futuresgateway.yisheng.rabbitmq.message;

import com.future.api.es.external.quote.bean.TapAPIQuoteWhole;
import com.future.api.es.external3.quote.bean.StkData;

public class EsQuoteInfo {

	/**
	 * api类型
	 * <ul>
	 * <li>3表示3.0</li>
	 * <li>9表示9.0</li>
	 * </ul>
	 */
	private int apiType;
	/**
	 * 9.0行情信息
	 */
	private TapAPIQuoteWhole info9;
	/**
	 * 3.0行情信息
	 */
	private StkData info3;
	/**
	 * 行情索引序号
	 */
	private long quoteIndex;

	public int getApiType() {
		return apiType;
	}

	public void setApiType(int apiType) {
		this.apiType = apiType;
	}

	public TapAPIQuoteWhole getInfo9() {
		return info9;
	}

	public void setInfo9(TapAPIQuoteWhole info9) {
		this.info9 = info9;
	}

	public StkData getInfo3() {
		return info3;
	}

	public void setInfo3(StkData info3) {
		this.info3 = info3;
	}

	public long getQuoteIndex() {
		return quoteIndex;
	}

	public void setQuoteIndex(long quoteIndex) {
		this.quoteIndex = quoteIndex;
	}

}
