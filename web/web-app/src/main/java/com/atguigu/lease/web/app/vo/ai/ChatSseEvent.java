package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSE 流式事件载体")
public class ChatSseEvent {

    @Schema(description = "事件类型:meta/message/recommendations/citations/trajectory/done/error")
    private String type;

    @Schema(description = "按事件类型返回元数据、文本、结构化列表、轨迹或完成信息")
    private Object payload;
}
