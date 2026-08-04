SET NAMES utf8mb4;

INSERT INTO `province_info` (`id`, `name`, `create_time`, `update_time`, `is_deleted`) VALUES
  (910001, '上海市', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `is_deleted` = 0;

INSERT INTO `city_info` (`id`, `name`, `province_id`, `create_time`, `update_time`, `is_deleted`) VALUES
  (910001, '上海市', 910001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `province_id` = VALUES(`province_id`), `is_deleted` = 0;

INSERT INTO `district_info` (`id`, `name`, `city_id`, `create_time`, `update_time`, `is_deleted`) VALUES
  (910001, '浦东新区', 910001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (910002, '徐汇区', 910001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `city_id` = VALUES(`city_id`), `is_deleted` = 0;

INSERT INTO `apartment_info` (
  `id`, `name`, `introduction`, `district_id`, `district_name`, `city_id`, `city_name`,
  `province_id`, `province_name`, `address_detail`, `latitude`, `longitude`, `phone`,
  `is_release`, `create_time`, `update_time`, `is_deleted`, `own_id`
) VALUES
  (920001, '27公寓张江店', '近地铁、可拎包入住的科技园社区。', 910001, '浦东新区', 910001, '上海市', 910001, '上海市', '张江路27号', '31.2031', '121.6019', '13800000000', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 1),
  (920002, '27公寓陆家嘴店', '通勤便利，配备公共会客区与健身空间。', 910001, '浦东新区', 910001, '上海市', 910001, '上海市', '浦东南路127号', '31.2354', '121.5057', '13800000000', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 1),
  (920003, '27公寓徐家汇店', '成熟商圈内的安静长租社区。', 910002, '徐汇区', 910001, '上海市', 910001, '上海市', '漕溪北路227号', '31.1927', '121.4378', '13800000000', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `introduction` = VALUES(`introduction`),
  `district_id` = VALUES(`district_id`), `district_name` = VALUES(`district_name`),
  `city_id` = VALUES(`city_id`), `city_name` = VALUES(`city_name`),
  `province_id` = VALUES(`province_id`), `province_name` = VALUES(`province_name`),
  `address_detail` = VALUES(`address_detail`), `latitude` = VALUES(`latitude`),
  `longitude` = VALUES(`longitude`), `phone` = VALUES(`phone`),
  `is_release` = VALUES(`is_release`), `is_deleted` = 0;

INSERT INTO `room_info` (`id`, `room_number`, `rent`, `apartment_id`, `is_release`, `create_time`, `update_time`, `is_deleted`) VALUES
  (930001, 'A101', 2299.00, 920001, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (930002, 'A202', 2699.00, 920001, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (930003, 'B1203', 3299.00, 920002, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (930004, 'B1508', 3899.00, 920002, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (930005, 'C306', 2999.00, 920003, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (930006, 'C509', 3499.00, 920003, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
  `room_number` = VALUES(`room_number`), `rent` = VALUES(`rent`),
  `apartment_id` = VALUES(`apartment_id`), `is_release` = VALUES(`is_release`), `is_deleted` = 0;

INSERT INTO `label_info` (`id`, `type`, `name`, `create_time`, `update_time`, `is_deleted`) VALUES
  (940001, 1, '近地铁', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (940002, 1, '品牌公寓', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (940003, 2, '朝南', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (940004, 2, '独立卫浴', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `type` = VALUES(`type`), `name` = VALUES(`name`), `is_deleted` = 0;

INSERT INTO `apartment_label` (`id`, `apartment_id`, `label_id`, `create_time`, `update_time`, `is_deleted`) VALUES
  (941001, 920001, 940001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (941002, 920002, 940002, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (941003, 920003, 940001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `apartment_id` = VALUES(`apartment_id`), `label_id` = VALUES(`label_id`), `is_deleted` = 0;

INSERT INTO `room_label` (`id`, `room_id`, `label_id`, `create_time`, `update_time`, `is_deleted`) VALUES
  (942001, 930001, 940003, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (942002, 930001, 940004, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (942003, 930002, 940004, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (942004, 930003, 940003, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (942005, 930004, 940004, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (942006, 930005, 940003, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (942007, 930006, 940004, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `room_id` = VALUES(`room_id`), `label_id` = VALUES(`label_id`), `is_deleted` = 0;

INSERT INTO `payment_type` (`id`, `name`, `pay_month_count`, `additional_info`, `create_time`, `update_time`, `is_deleted`) VALUES
  (950001, '月付', 1, '押一付一', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (950002, '季付', 3, '押一付三', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `pay_month_count` = VALUES(`pay_month_count`),
  `additional_info` = VALUES(`additional_info`), `is_deleted` = 0;

INSERT INTO `lease_term` (`id`, `month_count`, `unit`, `create_time`, `update_time`, `is_deleted`) VALUES
  (960001, 12, '月', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `month_count` = VALUES(`month_count`), `unit` = VALUES(`unit`), `is_deleted` = 0;

INSERT INTO `room_payment_type` (`id`, `room_id`, `payment_type_id`, `create_time`, `update_time`, `is_deleted`) VALUES
  (961001, 930001, 950001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (961002, 930002, 950001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (961003, 930003, 950002, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (961004, 930004, 950002, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (961005, 930005, 950001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (961006, 930006, 950001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `room_id` = VALUES(`room_id`), `payment_type_id` = VALUES(`payment_type_id`), `is_deleted` = 0;

INSERT INTO `room_lease_term` (`id`, `room_id`, `lease_term_id`, `create_time`, `update_time`, `is_deleted`) VALUES
  (962001, 930001, 960001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (962002, 930002, 960001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (962003, 930003, 960001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (962004, 930004, 960001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (962005, 930005, 960001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (962006, 930006, 960001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `room_id` = VALUES(`room_id`), `lease_term_id` = VALUES(`lease_term_id`), `is_deleted` = 0;

INSERT INTO `graph_info` (`id`, `name`, `item_type`, `item_id`, `url`, `create_time`, `update_time`, `is_deleted`) VALUES
  (970001, '张江店外观', 1, 920001, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970002, '陆家嘴店外观', 1, 920002, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970003, '徐家汇店外观', 1, 920003, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970101, 'A101室内', 2, 930001, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970102, 'A202室内', 2, 930002, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970103, 'B1203室内', 2, 930003, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970104, 'B1508室内', 2, 930004, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970105, 'C306室内', 2, 930005, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (970106, 'C509室内', 2, 930006, 'http://localhost:9000/lease/demo-room.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `item_type` = VALUES(`item_type`), `item_id` = VALUES(`item_id`),
  `url` = VALUES(`url`), `is_deleted` = 0;

INSERT INTO `user_info` (`id`, `phone`, `password`, `avatar_url`, `nickname`, `status`, `create_time`, `update_time`, `is_deleted`) VALUES
  (990001, '13800000000', NULL, NULL, '演示租客', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `phone` = VALUES(`phone`), `nickname` = VALUES(`nickname`), `status` = 1, `is_deleted` = 0;
