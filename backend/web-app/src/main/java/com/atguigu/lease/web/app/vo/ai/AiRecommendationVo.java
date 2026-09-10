package com.atguigu.lease.web.app.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationVo {

    private Long roomId;
    private Long apartmentId;
    private String apartment;
    private String roomNumber;
    private BigDecimal rent;
}
