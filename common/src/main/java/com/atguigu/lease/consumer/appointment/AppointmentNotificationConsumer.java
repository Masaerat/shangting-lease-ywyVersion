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
        System.out.println("=====================================");
        System.out.println("📧 处理创建通知");
        System.out.println("📋 预约ID: " + message.getAppointmentId());
        System.out.println("👤 用户: " + message.getName());
        System.out.println("📱 手机: " + message.getPhone());
        System.out.println("📅 预约时间: " + message.getAppointmentTime());
        System.out.println("💬 通知内容: " + message.getMessageContent());
        System.out.println("✅ 处理完成！");
        System.out.println("=====================================");

        // TODO: 集成邮件服务
        // emailService.sendEmail(message.getEmail(), "预约成功通知", message.getMessageContent());

        // TODO: 集成APP推送
        // pushService.sendNotification(message.getUserId(), "预约成功", message.getMessageContent());
    }

    /**
     * 处理取消通知
     */
    private void handleCancelNotification(AppointmentNotificationMessage message) {
        System.out.println("=====================================");
        System.out.println("❌ 处理取消通知");
        System.out.println("📋 预约ID: " + message.getAppointmentId());
        System.out.println("👤 用户: " + message.getName());
        System.out.println("📱 手机: " + message.getPhone());
        System.out.println("💬 取消原因: " + message.getMessageContent());
        System.out.println("✅ 处理完成！");
        System.out.println("=====================================");

        // TODO: 记录取消日志到数据库
        // cancellationLogService.logCancellation(message.getAppointmentId(), message.getUserId(), message.getMessageContent());
    }

    /**
     * 处理更新通知
     */
    private void handleUpdateNotification(AppointmentNotificationMessage message) {
        System.out.println("=====================================");
        System.out.println("🔄 处理更新通知");
        System.out.println("📋 预约ID: " + message.getAppointmentId());
        System.out.println("👤 用户: " + message.getName());
        System.out.println("📊 新状态: " + message.getAppointmentStatus());
        System.out.println("📝 更新内容: " + message.getMessageContent());
        System.out.println("⏰ 更新时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("✅ 处理完成！");
        System.out.println("=====================================");
    }
}