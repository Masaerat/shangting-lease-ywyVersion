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

    @Schema(description = "事件类型:message(增量)/done(完成,带引用)/error")
    private String type;

    @Schema(description = "载荷:String(token/错误信息) / List<RoomCitationVo>")
    private Object payload;
}
