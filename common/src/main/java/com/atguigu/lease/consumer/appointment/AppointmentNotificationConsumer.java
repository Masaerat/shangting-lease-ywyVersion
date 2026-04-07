package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AppointmentNotificationConsumer {

    @RabbitListener(queues = "appointment.notify.queue")
    public void handleNotification(AppointmentNotificationMessage message) {
        System.out.println("收到通知消息: " + message);

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
                    System.out.println("未知的通知类型: " + message.getNotificationType());
            }

        } catch (Exception e) {
            System.err.println("处理通知消息失败: " + e.getMessage());
            e.printStackTrace();
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