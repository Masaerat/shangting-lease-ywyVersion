package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AppointmentMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentMessageConsumer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * 处理预约创建消息
     */
    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_CREATE_QUEUE)
    public void handleAppointmentCreate(AppointmentMessage message) {
        logger.info("收到预约创建消息，预约ID: {}", message.getAppointmentId());

        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                // 1. 发送确认短信
                sendConfirmationSMS(message);

                // 2. 发送通知消息到通知队列
                AppointmentNotificationMessage notificationMessage = AppointmentNotificationMessage.builder()
                        .appointmentId(message.getAppointmentId())
                        .userId(message.getUserId())
                        .name(message.getName())
                        .phone(message.getPhone())
                        .apartmentId(message.getApartmentId())
                        .appointmentTime(message.getAppointmentTime())
                        .additionalInfo(message.getAdditionalInfo())
                        .appointmentStatus(message.getAppointmentStatus())
                        .notificationType("CREATE")
                        .messageContent("您的预约已成功创建，预约时间：" + message.getAppointmentTime())
                        .createTime(new Date())
                        .build();

                rabbitTemplate.convertAndSend(RabbitMQConfig.APPOINTMENT_EXCHANGE, RabbitMQConfig.NOTIFY_ROUTING_KEY, notificationMessage);
                logger.info("预约创建消息处理成功，已发送通知消息，预约ID: {}", message.getAppointmentId());
                return; // 成功则返回

            } catch (Exception e) {
                retryCount++;
                logger.error("处理预约创建消息失败，尝试次数: {}/{}，错误: {}", retryCount, MAX_RETRIES, e.getMessage());
                if (retryCount >= MAX_RETRIES) {
                    logger.error("处理预约创建消息失败，已重试" + MAX_RETRIES + "次，预约ID: {}", message.getAppointmentId());
                    // 可以在这里添加死信处理或告警逻辑
                    return;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("处理预约创建消息被中断，预约ID: {}", message.getAppointmentId());
                    return;
                }
            }
        }
    }

    /**
     * 处理预约过期消息（来自死信队列）
     * 注意：此方法保留用于兼容性，但目前不会自动取消预约
     */
    @RabbitListener(queues = "appointment.dlx.queue")
    public void handleAppointmentExpire(AppointmentMessage message) {
        System.out.println("收到预约过期消息: " + message);

        // 目前只记录日志，不进行自动取消操作
        System.out.println("预约已过期，但不会自动取消: " + message.getAppointmentId());
        System.out.println("如需取消，请管理员手动操作");
    }

    /**
     * 发送确认短信
     */
    private void sendConfirmationSMS(AppointmentMessage message) {
        // 这里集成阿里云短信服务
        System.out.println("发送确认短信到: " + message.getPhone());
        System.out.println("预约信息: " + message.getName() + " 预约时间: " + message.getAppointmentTime());

        // 实际项目中这里调用短信服务API
        // smsService.sendSMS(message.getPhone(), "您的预约已成功确认");
    }
}