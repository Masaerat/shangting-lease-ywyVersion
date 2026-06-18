package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "AI推荐房源")
public class AiRecommendedRoomVo {

    @Schema(description = "房间Id")
    private Long roomId;

    @Schema(description = "房间号")
    private String roomNumber;

    @Schema(description = "月租金")
    private BigDecimal rent;

    @Schema(description = "公寓名称")
    private String apartmentName;

    @Schema(description = "公寓地址")
    private String address;

    @Schema(description = "推荐理由")
    private String reason;

    @Schema(description = "房源标签")
    private List<String> labels;
}
