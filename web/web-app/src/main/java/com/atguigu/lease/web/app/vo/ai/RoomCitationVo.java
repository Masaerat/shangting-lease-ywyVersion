package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "推荐房源引用")
public class RoomCitationVo {

    @Schema(description = "房间主键")
    private Long roomId;

    @Schema(description = "公寓名(来源)")
    private String apartment;

    @Schema(description = "房间号")
    private String roomNumber;

    @Schema(description = "月租金")
    private BigDecimal rent;

    @Schema(description = "来源类型:rooms/doc")
    private String source;
}
