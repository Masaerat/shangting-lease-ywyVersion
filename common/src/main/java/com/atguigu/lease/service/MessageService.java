package com.atguigu.lease.service;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import com.atguigu.lease.util.DelayMessageUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private DelayMessageUtil delayMessageUtil;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * 发送预约创建消息（带重试机制）
     */
    public void sendAppointmentCreateMessage(AppointmentMessage message) {
        logger.info("开始发送预约创建消息，预约ID: {}", message.getAppointmentId());
        sendWithRetry(() -> {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.APPOINTMENT_EXCHANGE,
                RabbitMQConfig.CREATE_ROUTING_KEY,
                message
            );
            logger.info("预约创建消息发送成功，预约ID: {}", message.getAppointmentId());
        }, "发送预约创建消息失败");
    }

    /**
     * 发送预约状态变更消息（带重试机制）
     */
    public void sendAppointmentStatusChangeMessage(
            Long appointmentId,
            Long userId,
            String name,
            String phone,
            Long apartmentId,
            java.util.Date appointmentTime,
            String additionalInfo,
            String appointmentStatus,
            String notificationType,
            String messageContent) {

        logger.info("开始发送预约状态变更消息，预约ID: {}", appointmentId);
        AppointmentNotificationMessage message = AppointmentNotificationMessage.builder()
                .appointmentId(appointmentId)
                .userId(userId)
                .name(name)
                .phone(phone)
                .apartmentId(apartmentId)
                .appointmentTime(appointmentTime)
                .additionalInfo(additionalInfo)
                .appointmentStatus(appointmentStatus)
                .notificationType(notificationType)
                .messageContent(messageContent)
                .createTime(new java.util.Date())
                .build();

        sendWithRetry(() -> {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.APPOINTMENT_EXCHANGE,
                RabbitMQConfig.NOTIFY_ROUTING_KEY,
                message
            );
            logger.info("预约状态变更消息发送成功，预约ID: {}", appointmentId);
        }, "发送预约状态变更消息失败");
    }


    /**
     * 带重试机制的发送方法
     */
    private void sendWithRetry(Runnable sendAction, String errorMessage) {
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                sendAction.run();
                return; // 成功则返回
            } catch (Exception e) {
                retryCount++;
                logger.error("消息发送失败，尝试次数: {}/{}，错误: {}", retryCount, MAX_RETRIES, e.getMessage());
                if (retryCount >= MAX_RETRIES) {
                    throw new RuntimeException(errorMessage + "，已重试" + MAX_RETRIES + "次仍未成功", e);
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
    }
}