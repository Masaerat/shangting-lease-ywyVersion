package com.atguigu.lease.web.admin.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识库文档分页查询条件")
public class KnowledgeDocQueryVo {

    @Schema(description = "当前页")
    private Integer current = 1;

    @Schema(description = "每页大小")
    private Integer size = 10;

    @Schema(description = "逻辑分区")
    private String namespace;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "文件名关键字")
    private String keyword;
}
