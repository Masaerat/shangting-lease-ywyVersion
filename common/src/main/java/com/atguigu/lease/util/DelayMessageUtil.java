package com.atguigu.lease.util;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import org.springframework.amqp.core.Message;
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
     * 发送自定义延迟的消息
     */
    public void sendDelayedMessage(String exchange, String routingKey, Object message, long delayInMilliseconds) {
        MessagePostProcessor messagePostProcessor = msg -> {
            // 设置延迟时间（使用x-delay header）
            msg.getMessageProperties().setHeader("x-delay", delayInMilliseconds);
            return msg;
        };

        rabbitTemplate.convertAndSend(exchange, routingKey, message, messagePostProcessor);
    }
}