CREATE TABLE user_notification (
    id bigint NOT NULL AUTO_INCREMENT,
    event_key varchar(160) NOT NULL,
    event_id bigint DEFAULT NULL,
    appointment_id bigint NOT NULL,
    user_id bigint NOT NULL,
    notification_type varchar(16) NOT NULL,
    content varchar(1000) NOT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at datetime DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_event (event_key),
    KEY idx_notification_user (user_id, id),
    KEY idx_notification_appointment (appointment_id, user_id, notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

ALTER TABLE appointment_event_outbox
    ADD KEY idx_outbox_aggregate (aggregate_id, event_type, id);
