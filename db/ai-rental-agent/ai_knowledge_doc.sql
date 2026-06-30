-- AI 知识库文档元数据表(存于 MySQL 业务库,与 MyBatis-Plus 一起管理)
-- 向量本身存于 PostgreSQL + pgvector(Spring AI PgVectorStore 自管的 vector_store 表)
CREATE TABLE IF NOT EXISTS `ai_knowledge_doc` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `doc_name`         VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `content_type`     VARCHAR(128) COMMENT 'MIME 类型',
  `size_bytes`       BIGINT       COMMENT '文件大小(字节)',
  `namespace`        VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '逻辑分区',
  `status`           VARCHAR(32)  NOT NULL COMMENT 'UPLOADING / INDEXED / FAILED',
  `chunk_count`      INT          COMMENT '分片数',
  `minio_object_key` VARCHAR(255) COMMENT 'MinIO 对象 key',
  `minio_bucket`     VARCHAR(128) COMMENT 'MinIO 桶名',
  `error_message`    VARCHAR(512) COMMENT '失败原因',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0 否 1 是)',
  PRIMARY KEY (`id`),
  KEY `idx_namespace_status` (`namespace`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识库文档元数据';
