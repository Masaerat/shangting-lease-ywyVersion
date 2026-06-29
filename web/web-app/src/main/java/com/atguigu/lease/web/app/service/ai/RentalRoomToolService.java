package com.atguigu.lease.web.app.service.ai;

import com.atguigu.lease.web.app.vo.ai.AiPreferenceVo;
import com.atguigu.lease.web.app.vo.ai.AiRecommendedRoomVo;

import java.util.List;

public interface RentalRoomToolService {

    List<AiRecommendedRoomVo> searchRooms(AiPreferenceVo preferences, int limit);
}
