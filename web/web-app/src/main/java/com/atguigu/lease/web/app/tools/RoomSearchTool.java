package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.RoomInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 房源结构化检索工具:供 ChatClient 作为 @Tool 调用,按明确条件精确筛选在租房源。
 */
@Component
public class RoomSearchTool {

    public record RoomHit(Long roomId, String apartment, String roomNumber, BigDecimal rent, Long apartmentId) {}

    @Autowired private RoomInfoService roomInfoService;
    @Autowired private ApartmentInfoService apartmentInfoService;

    @Tool(description = "按城市/区/最高月租金精确筛选在租房源,返回候选房源列表(用于用户给出明确位置或预算条件时)。所有参数均可为空。")
    public List<RoomHit> searchRooms(
            @ToolParam(required = false, description = "城市名,如 北京") String city,
            @ToolParam(required = false, description = "区,如 朝阳区") String district,
            @ToolParam(required = false, description = "最高月租金(元),如 3000") BigDecimal maxMonthlyRent) {

        // 城市/区 经 ApartmentInfo 过滤,得到候选公寓 id 集
        List<Long> apartmentIds = null;
        boolean hasLocation = (city != null && !city.isBlank()) || (district != null && !district.isBlank());
        if (hasLocation) {
            LambdaQueryWrapper<ApartmentInfo> aqw = new LambdaQueryWrapper<>();
            aqw.like(city != null && !city.isBlank(), ApartmentInfo::getCityName, city);
            aqw.like(district != null && !district.isBlank(), ApartmentInfo::getDistrictName, district);
            List<ApartmentInfo> apts = apartmentInfoService.list(aqw);
            apartmentIds = apts.stream().map(ApartmentInfo::getId).toList();
            if (apartmentIds.isEmpty()) return Collections.emptyList();
        }

        LambdaQueryWrapper<RoomInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(RoomInfo::getIsRelease, ReleaseStatus.RELEASED); // 仅在租
        if (maxMonthlyRent != null) qw.le(RoomInfo::getRent, maxMonthlyRent);
        if (apartmentIds != null) qw.in(RoomInfo::getApartmentId, apartmentIds);

        List<RoomInfo> rooms = roomInfoService.list(qw);
        if (rooms.isEmpty()) return Collections.emptyList();

        return rooms.stream().limit(20).map(r -> {
            ApartmentInfo apt = r.getApartmentId() == null ? null : apartmentInfoService.getById(r.getApartmentId());
            return new RoomHit(r.getId(), apt == null ? null : apt.getName(),
                    r.getRoomNumber(), r.getRent(), r.getApartmentId());
        }).toList();
    }
}
