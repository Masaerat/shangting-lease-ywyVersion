package com.atguigu.lease.web.app.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "AI租房偏好")
public class AiPreferenceVo {

    @Schema(description = "省份Id")
    private Long provinceId;

    @Schema(description = "城市Id")
    private Long cityId;

    @Schema(description = "区域Id")
    private Long districtId;

    @Schema(description = "最低租金")
    private BigDecimal minRent;

    @Schema(description = "最高租金")
    private BigDecimal maxRent;

    @Schema(description = "支付方式Id")
    private Long paymentTypeId;

    @Schema(description = "价格排序方式")
    private String orderType;
}
