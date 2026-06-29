package com.atguigu.lease.web.app.service.ai.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RentalIntent {

    private boolean roomSearchRequested;

    private boolean rentalQuestionRequested;

    private BigDecimal minRent;

    private BigDecimal maxRent;
}
