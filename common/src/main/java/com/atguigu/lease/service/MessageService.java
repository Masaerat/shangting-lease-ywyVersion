package com.atguigu.lease.service;

import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import com.atguigu.lease.util.DelayMessageUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private DelayMessageUtil delayMessageUtil;

    /**
     * 发送预约创建消息
     */
    public void sendAppointmentCreateMessage(AppointmentMessage message) {
        rabbitTemplate.convertAndSend(
            "appointment.exchange",
            "view.appointment.create",
            message
        );
    }

    /**
     * 发送预约状态变更消息
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

        rabbitTemplate.convertAndSend(
            "appointment.exchange",
            "view.appointment.notify",
            message
        );
    }

    /**
     * 发送延迟消息（用于自动取消）
     */
    public void sendDelayedCancelMessage(AppointmentMessage message) {
        // 使用DelayMessageUtil发送延迟消息
        delayMessageUtil.sendDelayedCancelMessage(message);
    }
}