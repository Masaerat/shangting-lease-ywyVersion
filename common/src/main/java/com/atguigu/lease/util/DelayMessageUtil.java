package com.atguigu.lease.util;

import com.atguigu.lease.message.appointment.AppointmentMessage;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DelayMessageUtil {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送延迟预约取消消息
     */
    public void sendDelayedCancelMessage(AppointmentMessage message) {
        MessagePostProcessor messagePostProcessor = msg -> {
            // 设置消息过期时间（24小时）
            msg.getMessageProperties().setExpiration("86400000");
            // 设置延迟时间（毫秒）
            msg.getMessageProperties().setDelay(86400000);
            return msg;
        };

        rabbitTemplate.convertAndSend(
            "appointment.exchange",
            "view.appointment.create",
            message,
            messagePostProcessor
        );
    }

    /**
     * 发送自定义延迟的消息
     */
    public void sendDelayedMessage(String exchange, String routingKey, Object message, long delayInMilliseconds) {
        MessagePostProcessor messagePostProcessor = msg -> {
            // 设置消息过期时间
            msg.getMessageProperties().setExpiration(String.valueOf(delayInMilliseconds));
            // 设置延迟时间
            msg.getMessageProperties().setDelay(delayInMilliseconds);
            return msg;
        };

        rabbitTemplate.convertAndSend(exchange, routingKey, message, messagePostProcessor);
    }
}