package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.LabelInfo;
import com.atguigu.lease.web.app.service.RoomInfoService;
import com.atguigu.lease.web.app.service.ai.RentalRoomToolService;
import com.atguigu.lease.web.app.vo.ai.AiPreferenceVo;
import com.atguigu.lease.web.app.vo.ai.AiRecommendedRoomVo;
import com.atguigu.lease.web.app.vo.room.RoomItemVo;
import com.atguigu.lease.web.app.vo.room.RoomQueryVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RentalRoomToolServiceImpl implements RentalRoomToolService {

    private final RoomInfoService roomInfoService;

    public RentalRoomToolServiceImpl(RoomInfoService roomInfoService) {
        this.roomInfoService = roomInfoService;
    }

    @Override
    public List<AiRecommendedRoomVo> searchRooms(AiPreferenceVo preferences, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        RoomQueryVo queryVo = toRoomQuery(preferences);
        IPage<RoomItemVo> page = roomInfoService.pageRoomItemByQuery(new Page<>(1, safeLimit), queryVo);
        if (page == null || page.getRecords() == null) {
            return Collections.emptyList();
        }
        return page.getRecords().stream()
                .map(this::toRecommendedRoom)
                .toList();
    }

    private RoomQueryVo toRoomQuery(AiPreferenceVo preferences) {
        RoomQueryVo queryVo = new RoomQueryVo();
        if (preferences != null) {
            BeanUtils.copyProperties(preferences, queryVo);
        }
        if (queryVo.getOrderType() == null) {
            queryVo.setOrderType("asc");
        }
        return queryVo;
    }

    private AiRecommendedRoomVo toRecommendedRoom(RoomItemVo itemVo) {
        AiRecommendedRoomVo roomVo = new AiRecommendedRoomVo();
        roomVo.setRoomId(itemVo.getId());
        roomVo.setRoomNumber(itemVo.getRoomNumber());
        roomVo.setRent(itemVo.getRent());

        ApartmentInfo apartmentInfo = itemVo.getApartmentInfo();
        if (apartmentInfo != null) {
            roomVo.setApartmentName(apartmentInfo.getName());
            roomVo.setAddress(apartmentInfo.getDistrictName() + apartmentInfo.getAddressDetail());
        }

        List<String> labels = itemVo.getLabelInfoList() == null ? List.of() :
                itemVo.getLabelInfoList().stream()
                        .map(LabelInfo::getName)
                        .toList();
        roomVo.setLabels(labels);
        roomVo.setReason(buildReason(roomVo));
        return roomVo;
    }

    private String buildReason(AiRecommendedRoomVo roomVo) {
        String apartmentName = roomVo.getApartmentName() == null ? "该房源" : roomVo.getApartmentName();
        String rent = roomVo.getRent() == null ? "租金待确认" : "月租金约" + roomVo.getRent() + "元";
        return apartmentName + rent + "，可进入详情页继续查看户型、设施和预约时间。";
    }
}
