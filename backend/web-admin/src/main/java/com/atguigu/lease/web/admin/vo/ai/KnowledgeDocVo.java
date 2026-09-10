package com.atguigu.lease.web.admin.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识库文档视图")
public class KnowledgeDocVo {

    @Schema(description = "文档主键")
    private Long id;

    @Schema(description = "原始文件名")
    private String docName;

    @Schema(description = "MIME 类型")
    private String contentType;

    @Schema(description = "文件大小(字节)")
    private Long sizeBytes;

    @Schema(description = "逻辑分区")
    private String namespace;

    @Schema(description = "状态:UPLOADING / INDEXED / FAILED")
    private String status;

    @Schema(description = "分片数")
    private Integer chunkCount;
}
