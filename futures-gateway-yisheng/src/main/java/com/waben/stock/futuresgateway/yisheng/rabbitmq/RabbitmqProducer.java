package com.waben.stock.futuresgateway.yisheng.rabbitmq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.waben.stock.futuresgateway.yisheng.util.JacksonUtil;

@Component
public class RabbitmqProducer {

	@Autowired
	private RabbitTemplate template;

	public void sendMessage(String queueName, Object message) {
		template.convertAndSend(queueName, JacksonUtil.encode(message));
	}

}
