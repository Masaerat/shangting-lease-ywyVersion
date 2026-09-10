package com.atguigu.lease.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String virtualHost;

    // 主交换机
    public static final String APPOINTMENT_EXCHANGE = "appointment.exchange";
    public static final String DIRECT_EXCHANGE_TYPE = "direct";

    // 队列名称
    public static final String APPOINTMENT_CREATE_QUEUE = "appointment.create.queue";
    public static final String APPOINTMENT_NOTIFY_QUEUE = "appointment.notify.queue";

    // 死信交换机和队列
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLX_QUEUE = "appointment.dlx.queue";

    // 路由键
    public static final String CREATE_ROUTING_KEY = "view.appointment.create";
    public static final String NOTIFY_ROUTING_KEY = "view.appointment.notify";
    public static final String EXPIRE_ROUTING_KEY = "view.appointment.expire";
    public static final String DLX_ROUTING_KEY = "appointment.dlx";

    // Queue retention only: expiration is not an appointment reminder or cancellation.
    public static final long TTL_2_HOURS = 2 * 60 * 60 * 1000L;

    /**
     * 主交换机
     */
    @Bean
    public DirectExchange appointmentExchange() {
        return new DirectExchange(APPOINTMENT_EXCHANGE);
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    /**
     * 预约创建队列（带死信交换机配置）
     */
    @Bean
    public Queue appointmentCreateQueue() {
        return QueueBuilder.durable(APPOINTMENT_CREATE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .withArgument("x-message-ttl", TTL_2_HOURS) // 2小时后过期
                .build();
    }

    /**
     * 通知队列
     */
    @Bean
    public Queue appointmentNotifyQueue() {
        return QueueBuilder.durable(APPOINTMENT_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY).build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    /**
     * 绑定：主交换机到创建队列
     */
    @Bean
    public Binding createBinding() {
        return BindingBuilder.bind(appointmentCreateQueue())
                .to(appointmentExchange())
                .with(CREATE_ROUTING_KEY);
    }

    /**
     * 绑定：主交换机到通知队列
     */
    @Bean
    public Binding notifyBinding() {
        return BindingBuilder.bind(appointmentNotifyQueue())
                .to(appointmentExchange())
                .with(NOTIFY_ROUTING_KEY);
    }

    /**
     * 绑定：死信交换机到死信队列
     */
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }


/**
 * 配置RabbitTemplate
 */
@Bean
public Jackson2JsonMessageConverter appointmentMessageConverter() {
    // Also discovered by Boot's Rabbit listener factory; producer-only configuration is insufficient.
    return new Jackson2JsonMessageConverter("com.atguigu.lease.message.appointment");
}

@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter appointmentMessageConverter) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMandatory(true);
    // 设置消息转换器
    rabbitTemplate.setMessageConverter(appointmentMessageConverter);
    // 设置消息确认回调
    rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
        Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);
        if (ack) {
            logger.info("消息发送成功: {}", correlationData == null ? "legacy" : correlationData.getId());
        } else {
            logger.error("消息发送失败: {}", cause);
        }
    });
    // 设置返回回调
    rabbitTemplate.setReturnsCallback(returned -> {
        Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);
        logger.error("消息未路由: exchange={}, routingKey={}, code={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode());
    });
    return rabbitTemplate;
}
}
