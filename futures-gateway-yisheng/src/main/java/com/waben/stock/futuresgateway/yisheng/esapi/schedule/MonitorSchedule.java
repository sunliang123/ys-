package com.waben.stock.futuresgateway.yisheng.esapi.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.waben.stock.futuresgateway.yisheng.esapi.esapi3.Es3QuoteWrapper;
import com.waben.stock.futuresgateway.yisheng.esapi.esapi9.Es9QuoteWrapper;
import com.waben.stock.futuresgateway.yisheng.esapi.esapi9.Es9TradeWrapper;

/**
 * 监控API
 * 
 * @author lma
 *
 */
@Component
@EnableScheduling
public class MonitorSchedule {

	Logger logger = LoggerFactory.getLogger(getClass());

	@Value("${es.api.type}")
	private int esApiType;

	@Autowired
	private Es3QuoteWrapper quote3Wrapper;

	@Autowired
	private Es9QuoteWrapper quote9Wrapper;

	@Autowired
	private Es9TradeWrapper trade9Wrapper;

	/**
	 * 5点55的时候重新连接行情api和交易api
	 */
	@Scheduled(cron = "0 55 6 * * ?")
	public void monitor() {
		if (esApiType == 3) {
			try {
				quote3Wrapper.reconnect();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		} else if (esApiType == 9) {
			try {
				logger.info("MonitorSchedule日志1");
				quote9Wrapper.reconnect(true);
				logger.info("MonitorSchedule日志2");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			try {
				// trade9Wrapper.reconnect();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

}
