ALTER TABLE `view_appointment`
  ADD COLUMN `room_id` bigint DEFAULT NULL COMMENT '预约房间id' AFTER `apartment_id`,
  ADD KEY `idx_view_appointment_active_room` (`user_id`, `room_id`, `appointment_status`, `is_deleted`);
