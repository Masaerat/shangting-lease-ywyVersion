package com.atguigu.lease.web.admin.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档上传结果")
public class UploadResultVo {

    @Schema(description = "文档主键")
    private Long docId;

    @Schema(description = "处理状态")
    private String status;
}
