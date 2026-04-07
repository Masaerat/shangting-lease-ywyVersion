package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class AppointmentMessageConsumer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 处理预约创建消息
     */
    @RabbitListener(queues = "appointment.create.queue")
    public void handleAppointmentCreate(AppointmentMessage message) {
        System.out.println("收到预约创建消息: " + message);

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

            rabbitTemplate.convertAndSend("appointment.exchange", "view.appointment.notify", notificationMessage);
            System.out.println("已发送通知消息");

        } catch (Exception e) {
            System.err.println("处理预约创建消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理预约过期消息（来自死信队列）
     */
    @RabbitListener(queues = "appointment.dlx.queue")
    public void handleAppointmentExpire(AppointmentMessage message) {
        System.out.println("收到预约过期消息: " + message);

        try {
            // 自动取消未确认的预约
            // 这里可以调用service层的方法更新预约状态为已取消
            System.out.println("自动取消预约: " + message.getAppointmentId());

                // 发送取消通知
                AppointmentNotificationMessage notificationMessage = AppointmentNotificationMessage.builder()
                        .appointmentId(message.getAppointmentId())
                        .userId(message.getUserId())
                        .name(message.getName())
                        .phone(message.getPhone())
                        .apartmentId(message.getApartmentId())
                        .appointmentTime(message.getAppointmentTime())
                        .additionalInfo(message.getAdditionalInfo())
                        .appointmentStatus("CANCELED")
                        .notificationType("CANCEL")
                        .messageContent("您的预约已因超时自动取消")
                        .createTime(new Date())
                        .build();

            rabbitTemplate.convertAndSend("appointment.exchange", "view.appointment.notify", notificationMessage);
            System.out.println("已发送取消通知");

        } catch (Exception e) {
            System.err.println("处理预约过期消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 发送确认短信
     */
    private void sendConfirmationSMS(AppointmentMessage message) {
        // 模拟发送短信
        String smsContent = String.format(
            "【租赁系统】%s您好，您的预约已成功确认！预约时间：%s，请准时到场。如有疑问请联系客服。",
            message.getName(),
            message.getAppointmentTime()
        );

        System.out.println("=====================================");
        System.out.println("📱 短信发送中...");
        System.out.println("📞 收件人: " + message.getPhone());
        System.out.println("📄 内容: " + smsContent);
        System.out.println("✅ 短信发送成功！");
        System.out.println("=====================================");

        // TODO: 集成阿里云短信服务
        // smsService.sendSMS(message.getPhone(), smsContent);
    }
}