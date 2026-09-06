package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.notification.AppointmentNotificationStore;
import com.atguigu.lease.notification.AppointmentDeliveryStatus;
import com.atguigu.lease.notification.UserNotification;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppointmentStatusTool {
    private final AppointmentNotificationStore notifications;

    public AppointmentStatusTool(AppointmentNotificationStore notifications) {
        this.notifications = notifications;
    }

    @Tool(name = "get_appointment_status", description = "Read the authenticated user's appointment and MQ notification delivery status. Null means not found. DELIVERED only means in-app notification persisted, not SMS; PUBLISHED only means broker accepted. Never infer completion from a draft.")
    public AppointmentDeliveryStatus status(@ToolParam(description = "Confirmed appointment ID, NOT room ID or draft token") Long appointmentId,
                                             ToolContext context) {
        AppointmentDeliveryStatus result = notifications.status(AgentToolSupport.userId(context), appointmentId);
        AgentToolSupport.state(context).recordObservation("get_appointment_status", result);
        return result;
    }

    @Tool(name = "list_my_notifications", description = "Read recent persisted in-app appointment notifications for the authenticated user. Empty means no delivered notifications; it does not mean no appointment exists.")
    public List<UserNotification> list(@ToolParam(required = false, description = "Maximum results, 1-50") Integer limit,
                                       ToolContext context) {
        List<UserNotification> result = notifications.list(AgentToolSupport.userId(context), limit == null ? 20 : limit);
        AgentToolSupport.state(context).recordObservation("list_my_notifications", result);
        return result;
    }
}
