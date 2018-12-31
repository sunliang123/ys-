package com.waben.stock.futuresgateway.yisheng.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.waben.stock.futuresgateway.yisheng.rabbitmq.message.EsDeleteQuoteMessage;
import com.waben.stock.futuresgateway.yisheng.service.FuturesQuoteMinuteKService;
import com.waben.stock.futuresgateway.yisheng.service.FuturesQuoteService;

// @Service
public class QuoteDelQueue {

	Logger logger = LoggerFactory.getLogger(getClass());

	private BlockingQueue<EsDeleteQuoteMessage> delQuotes = new LinkedBlockingQueue<>();

	@Autowired
	private FuturesQuoteService quoteService;

	@Autowired
	private FuturesQuoteMinuteKService minuteKService;

	private ExecutorService excutor = Executors.newCachedThreadPool();

	@PostConstruct
	public void init() {
		// 开启开仓处理队列
		new Thread(new QueueHandler()).start();
	}

	private class QueueHandler implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					EsDeleteQuoteMessage delQuote = delQuotes.take();
					DelRunable job = new DelRunable(delQuote);
					excutor.execute(job);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private class DelRunable implements Runnable {

		private EsDeleteQuoteMessage delQuote;

		public DelRunable(EsDeleteQuoteMessage delQuote) {
			this.delQuote = delQuote;
		}

		@Override
		public void run() {
			try {
				if (delQuote.getType() != null && delQuote.getType() == 1) {
					quoteService.deleteFuturesQuote(delQuote.getCommodityNo(), delQuote.getContractNo(),
							delQuote.getQuoteId());
				} else if (delQuote.getType() != null && delQuote.getType() == 2) {
					minuteKService.deleteFuturesQuoteMinuteK(delQuote.getCommodityNo(), delQuote.getContractNo(),
							delQuote.getQuoteId());
				}
			} catch (Exception ex) {
				logger.error("消费删除易盛Quote消息异常!", ex);
			}
		}
	}

	public void addDelQuote(EsDeleteQuoteMessage delQuote) {
		this.delQuotes.add(delQuote);
	}
	
}
