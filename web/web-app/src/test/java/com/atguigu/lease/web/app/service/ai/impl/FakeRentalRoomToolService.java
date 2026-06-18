package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.RentalRoomToolService;
import com.atguigu.lease.web.app.vo.ai.AiPreferenceVo;
import com.atguigu.lease.web.app.vo.ai.AiRecommendedRoomVo;

import java.math.BigDecimal;
import java.util.List;

class FakeRentalRoomToolService implements RentalRoomToolService {

    @Override
    public List<AiRecommendedRoomVo> searchRooms(AiPreferenceVo preferences, int limit) {
        AiRecommendedRoomVo roomVo = new AiRecommendedRoomVo();
        roomVo.setRoomId(101L);
        roomVo.setRoomNumber("A101");
        roomVo.setRent(new BigDecimal("2100"));
        roomVo.setApartmentName("尚庭公寓");
        roomVo.setAddress("张江路100号");
        roomVo.setReason("租金接近预算，适合继续查看。");
        roomVo.setLabels(List.of("近地铁"));
        return List.of(roomVo);
    }
}
