package com.eden.common.config;

import com.eden.common.condition.RabbitCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 消息发布确认回调
 *
 * @author chenqw
 * @version 1.0
 * @since 2018/11/24
 */
@Component
@Conditional(RabbitCondition.class)
@Slf4j
public class RabbitTemplateConfig implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnCallback {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnCallback(this);
    }

    /**
     * 确认消息是否到达exchange
     *
     * @param correlationData correlation data for the callback. 消息相关数据
     * @param ack             true for ack, false for nack 是否接收消息成功
     * @param cause           An optional cause, for nack, when available, otherwise null. 接收失败的原因
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            log.info("消息到达交换机，消息ID：{}", correlationData.getId());
        } else {
            log.error("消息未到达交换机，消息ID：{}，失败原因：{}", correlationData.getId(), cause);
        }
    }

    /**
     * 启动消息失败返回，确认消息是否到达队列
     *
     * @param message    the returned message.
     * @param replyCode  the reply code.
     * @param replyText  the reply text.
     * @param exchange   the exchange.
     * @param routingKey the routing key.
     */
    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        log.error("消息体：{}", message);
        log.error("返回码：{}", replyCode);
        log.error("返回内容：{}", replyText);
        log.error("交换机：{}", exchange);
        log.error("路由键：{}", routingKey);
    }

}
