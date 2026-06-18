package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI回答引用来源")
public class AiCitationVo {

    @Schema(description = "引用标题")
    private String title;

    @Schema(description = "引用分类")
    private String category;

    @Schema(description = "引用来源")
    private String source;

    @Schema(description = "引用片段")
    private String snippet;
}
