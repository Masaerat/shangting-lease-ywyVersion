package com.atguigu.lease.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * AI 知识库文档元数据。继承 {@link BaseEntity} 获得 id / createTime / updateTime / isDeleted。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ai_knowledge_doc")
@Schema(description = "AI 知识库文档元数据")
public class AiKnowledgeDoc extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "原始文件名")
    @TableField("doc_name")
    private String docName;

    @Schema(description = "MIME 类型")
    @TableField("content_type")
    private String contentType;

    @Schema(description = "文件大小(字节)")
    @TableField("size_bytes")
    private Long sizeBytes;

    @Schema(description = "逻辑分区")
    @TableField("namespace")
    private String namespace;

    @Schema(description = "状态:UPLOADING / INDEXED / FAILED")
    @TableField("status")
    private String status;

    @Schema(description = "分片数")
    @TableField("chunk_count")
    private Integer chunkCount;

    @Schema(description = "MinIO 对象 key")
    @TableField("minio_object_key")
    private String minioObjectKey;

    @Schema(description = "MinIO 桶名")
    @TableField("minio_bucket")
    private String minioBucket;

    @Schema(description = "失败原因")
    @TableField("error_message")
    private String errorMessage;
}
