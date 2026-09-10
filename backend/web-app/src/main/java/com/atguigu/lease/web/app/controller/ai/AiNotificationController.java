package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.common.login.LoginUserHolder;
import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.notification.AppointmentNotificationStore;
import com.atguigu.lease.notification.AppointmentDeliveryStatus;
import com.atguigu.lease.notification.UserNotification;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app/ai")
public class AiNotificationController {
    private final AppointmentNotificationStore notifications;

    public AiNotificationController(AppointmentNotificationStore notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/appointments/{appointmentId}/status")
    public Result<AppointmentDeliveryStatus> status(@PathVariable Long appointmentId) {
        AppointmentDeliveryStatus status = notifications.status(userId(), appointmentId);
        if (status == null) throw new LeaseException("预约不存在", 404);
        return Result.ok(status);
    }

    @GetMapping("/notifications")
    public Result<List<UserNotification>> list(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(notifications.list(userId(), limit));
    }

    @PostMapping("/notifications/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        if (!notifications.markRead(userId(), id)) throw new LeaseException("通知不存在", 404);
        return Result.ok();
    }

    private Long userId() {
        if (LoginUserHolder.getLoginUser() == null) throw new LeaseException("请先登录", 401);
        return LoginUserHolder.getLoginUser().getUserId();
    }
}
