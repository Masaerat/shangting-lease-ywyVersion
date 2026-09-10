package com.atguigu.lease.notification;

import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public class AppointmentNotificationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public AppointmentNotificationStore(JdbcTemplate jdbcTemplate, TransactionTemplate transactions) {
        this.jdbc = jdbcTemplate;
        this.transactions = transactions;
    }

    public void persistCreated(AppointmentMessage message) {
        persist("appointment:" + message.getAppointmentId() + ":CREATE", message.getEventId(),
                message.getAppointmentId(), message.getUserId(), "CREATE", "您的看房预约已创建，请在预约详情中查看时间。此通知不代表短信已发送。");
    }

    public void persistLegacy(AppointmentNotificationMessage message) {
        String type = message.getNotificationType();
        if (type == null || !Set.of("CREATE", "CANCEL", "UPDATE").contains(type)) {
            throw new IllegalArgumentException("Unsupported notification type");
        }
        String key = "appointment:" + message.getAppointmentId() + ":" + type;
        if (!"CREATE".equals(type)) {
            key += ":" + (message.getMessageId() != null ? message.getMessageId()
                    : message.getCreateTime() != null ? message.getCreateTime().getTime() : message.getAppointmentStatus());
        }
        persist(key, null, message.getAppointmentId(), message.getUserId(), type, message.getMessageContent());
    }

    private void persist(String key, Long eventId, Long appointmentId, Long userId, String type, String content) {
        if (appointmentId == null || userId == null || content == null || content.isBlank() || content.length() > 1000) {
            throw new IllegalArgumentException("Invalid appointment notification");
        }
        transactions.executeWithoutResult(status -> {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM view_appointment WHERE id = ? AND user_id = ?",
                    Long.class, appointmentId, userId);
            if (count == null || count != 1) throw new IllegalArgumentException("Appointment owner mismatch");
            jdbc.update("""
                    INSERT INTO user_notification
                      (event_key, event_id, appointment_id, user_id, notification_type, content)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE id = id
                    """, key, eventId, appointmentId, userId, type, content);
        });
    }

    public List<UserNotification> list(Long userId, int limit) {
        requireUser(userId);
        return jdbc.query("""
                SELECT id, appointment_id, notification_type, content, created_at, read_at
                FROM user_notification WHERE user_id = ? ORDER BY id DESC LIMIT ?
                """, (rs, row) -> new UserNotification(rs.getLong("id"), rs.getLong("appointment_id"),
                rs.getString("notification_type"), rs.getString("content"),
                local(rs.getTimestamp("created_at")), local(rs.getTimestamp("read_at"))),
                userId, Math.max(1, Math.min(50, limit)));
    }

    public boolean markRead(Long userId, Long notificationId) {
        requireUser(userId);
        return Boolean.TRUE.equals(transactions.execute(status -> {
            List<Long> found = jdbc.query("SELECT id FROM user_notification WHERE id = ? AND user_id = ? FOR UPDATE",
                    (rs, row) -> rs.getLong(1), notificationId, userId);
            if (found.isEmpty()) return false;
            jdbc.update("UPDATE user_notification SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP) WHERE id = ? AND user_id = ?",
                    notificationId, userId);
            return true;
        }));
    }

    public AppointmentDeliveryStatus status(Long userId, Long appointmentId) {
        requireUser(userId);
        List<AppointmentDeliveryStatus> results = jdbc.query("""
                SELECT a.id, a.room_id, a.appointment_status, a.appointment_time,
                  (SELECT n.id FROM user_notification n WHERE n.appointment_id = a.id
                    AND n.user_id = a.user_id AND n.notification_type = 'CREATE' LIMIT 1) AS notification_id,
                  (SELECT o.status FROM appointment_event_outbox o WHERE o.aggregate_id = a.id
                    AND o.event_type = 'APPOINTMENT_CREATED' ORDER BY o.id DESC LIMIT 1) AS outbox_status
                FROM view_appointment a WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0
                """, (rs, row) -> {
            Long notificationId = rs.getObject("notification_id", Long.class);
            return new AppointmentDeliveryStatus(rs.getLong("id"), rs.getObject("room_id", Long.class),
                    rs.getObject("appointment_status", Integer.class), local(rs.getTimestamp("appointment_time")),
                    deliveryStatus(notificationId, rs.getString("outbox_status")), notificationId);
        }, appointmentId, userId);
        return results.isEmpty() ? null : results.getFirst();
    }

    public static String deliveryStatus(Long notificationId, String outboxStatus) {
        if (notificationId != null) return "DELIVERED";
        if ("DEAD".equals(outboxStatus)) return "FAILED";
        if ("PENDING".equals(outboxStatus) || "PUBLISHED".equals(outboxStatus)) return outboxStatus;
        return "UNKNOWN";
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("Authenticated user is required");
    }
}
