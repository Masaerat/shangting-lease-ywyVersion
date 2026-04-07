package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AppointmentNotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentNotificationConsumer.class);

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_NOTIFY_QUEUE)
    public void handleNotification(AppointmentNotificationMessage message) {
        logger.info("收到通知消息，预约ID: {}, 类型: {}", message.getAppointmentId(), message.getNotificationType());

        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                // 根据通知类型处理不同的通知
                switch (message.getNotificationType()) {
                    case "CREATE":
                        handleCreateNotification(message);
                        break;
                    case "CANCEL":
                        handleCancelNotification(message);
                        break;
                    case "UPDATE":
                        handleUpdateNotification(message);
                        break;
                    default:
                        logger.warn("未知的通知类型: {}", message.getNotificationType());
                }
                logger.info("通知消息处理成功，预约ID: {}, 类型: {}", message.getAppointmentId(), message.getNotificationType());
                return; // 成功则返回

            } catch (Exception e) {
                retryCount++;
                logger.error("处理通知消息失败，尝试次数: {}/{}，错误: {}", retryCount, MAX_RETRIES, e.getMessage());
                if (retryCount >= MAX_RETRIES) {
                    logger.error("处理通知消息失败，已重试" + MAX_RETRIES + "次，预约ID: {}", message.getAppointmentId());
                    // 可以在这里添加死信处理或告警逻辑
                    return;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("处理通知消息被中断，预约ID: {}", message.getAppointmentId());
                    return;
                }
            }
        }
    }

    /**
     * 处理创建通知
     */
    private void handleCreateNotification(AppointmentNotificationMessage message) {
        System.out.println("处理创建通知 - 预约ID: " + message.getAppointmentId());
        System.out.println("发送邮件通知给用户: " + message.getName());
        System.out.println("通知内容: " + message.getMessageContent());

        // 实际项目中这里可以：
        // 1. 发送邮件
        // 2. 推送APP通知
        // 3. 发送短信（如果需要）
    }

    /**
     * 处理取消通知
     */
    private void handleCancelNotification(AppointmentNotificationMessage message) {
        System.out.println("处理取消通知 - 预约ID: " + message.getAppointmentId());
        System.out.println("用户: " + message.getName() + " 的预约已被取消");
        System.out.println("取消原因: " + message.getMessageContent());

        // 实际项目中这里可以：
        // 1. 发送取消通知邮件
        // 2. 推送取消通知
        // 3. 记录取消日志
    }

    /**
     * 处理更新通知
     */
    private void handleUpdateNotification(AppointmentNotificationMessage message) {
        System.out.println("处理更新通知 - 预约ID: " + message.getAppointmentId());
        System.out.println("预约状态已更新为: " + message.getAppointmentStatus());
        System.out.println("更新时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 实际项目中这里可以：
        // 1. 发送状态变更通知
        // 2. 更新相关系统数据
        // 3. 通知相关人员
    }
}