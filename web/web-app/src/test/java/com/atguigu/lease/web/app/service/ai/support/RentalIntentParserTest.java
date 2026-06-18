package com.atguigu.lease.web.app.service.ai.support;

import com.atguigu.lease.web.app.service.ai.model.RentalIntent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RentalIntentParserTest {

    private final RentalIntentParser parser = new RentalIntentParser();

    @Test
    void parseDetectsMixedRoomSearchAndRentalQuestion() {
        RentalIntent intent = parser.parse("帮我找 2000 左右的房子，并说明押金怎么退");

        assertTrue(intent.isRoomSearchRequested());
        assertTrue(intent.isRentalQuestionRequested());
        assertEquals(new BigDecimal("1600.0"), intent.getMinRent());
        assertEquals(new BigDecimal("2400.0"), intent.getMaxRent());
    }
}
