package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.RoomInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class RoomSearchTool {

    private final RoomInfoService roomInfoService;
    private final ApartmentInfoService apartmentInfoService;

    public RoomSearchTool(RoomInfoService roomInfoService, ApartmentInfoService apartmentInfoService) {
        this.roomInfoService = roomInfoService;
        this.apartmentInfoService = apartmentInfoService;
    }

    public record RoomHit(Long roomId, String apartment, String roomNumber, BigDecimal rent, Long apartmentId) {
    }

    @Tool(description = "Search released rooms by city, district and monthly rent. All parameters are optional.")
    public List<RoomHit> searchRooms(
            @ToolParam(required = false, description = "City name") String city,
            @ToolParam(required = false, description = "District name") String district,
            @ToolParam(required = false, description = "Maximum monthly rent") BigDecimal maxMonthlyRent) {
        return searchRooms(city, district, null, maxMonthlyRent);
    }

    public List<RoomHit> searchRooms(String city, String district,
                                     BigDecimal minMonthlyRent, BigDecimal maxMonthlyRent) {
        List<Long> apartmentIds = apartmentIds(city, district);
        if (apartmentIds != null && apartmentIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<RoomInfo> query = new LambdaQueryWrapper<>();
        query.eq(RoomInfo::getIsRelease, ReleaseStatus.RELEASED);
        query.ge(minMonthlyRent != null, RoomInfo::getRent, minMonthlyRent);
        query.le(maxMonthlyRent != null, RoomInfo::getRent, maxMonthlyRent);
        if (apartmentIds != null) {
            query.in(RoomInfo::getApartmentId, apartmentIds);
        }

        return roomInfoService.list(query).stream().limit(20).map(room -> {
            ApartmentInfo apartment = room.getApartmentId() == null
                    ? null : apartmentInfoService.getById(room.getApartmentId());
            return new RoomHit(
                    room.getId(),
                    apartment == null ? null : apartment.getName(),
                    room.getRoomNumber(),
                    room.getRent(),
                    room.getApartmentId());
        }).toList();
    }

    private List<Long> apartmentIds(String city, String district) {
        boolean hasCity = city != null && !city.isBlank();
        boolean hasDistrict = district != null && !district.isBlank();
        if (!hasCity && !hasDistrict) {
            return null;
        }
        LambdaQueryWrapper<ApartmentInfo> query = new LambdaQueryWrapper<>();
        query.like(hasCity, ApartmentInfo::getCityName, city);
        query.like(hasDistrict, ApartmentInfo::getDistrictName, district);
        return apartmentInfoService.list(query).stream().map(ApartmentInfo::getId).toList();
    }
}
