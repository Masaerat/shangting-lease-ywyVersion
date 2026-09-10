package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.PaymentType;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.PaymentTypeService;
import com.atguigu.lease.web.app.service.RoomInfoService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class RoomDetailTool {

    private final RoomInfoService roomInfoService;
    private final ApartmentInfoService apartmentInfoService;
    private final PaymentTypeService paymentTypeService;

    public RoomDetailTool(RoomInfoService roomInfoService,
                          ApartmentInfoService apartmentInfoService,
                          PaymentTypeService paymentTypeService) {
        this.roomInfoService = roomInfoService;
        this.apartmentInfoService = apartmentInfoService;
        this.paymentTypeService = paymentTypeService;
    }

    @Tool(name = "get_room_detail", description = "Get current business details for one real released room by roomId.")
    public RoomDetailResult get(
            @ToolParam(description = "Room primary key returned by search_available_rooms") Long roomId,
            ToolContext context) {
        RoomInfo room = roomInfoService.getById(roomId);
        if (room == null || room.getIsRelease() != ReleaseStatus.RELEASED) {
            throw new IllegalArgumentException("Room is unavailable: " + roomId);
        }
        ApartmentInfo apartment = room.getApartmentId() == null
                ? null : apartmentInfoService.getById(room.getApartmentId());
        List<PaymentOption> paymentOptions = paymentTypeService.listByRoomId(roomId).stream()
                .map(item -> new PaymentOption(item.getName(), item.getPayMonthCount(), item.getAdditionalInfo()))
                .toList();
        RoomDetailResult result = new RoomDetailResult(
                room.getId(), room.getRoomNumber(), room.getRent(), room.getApartmentId(),
                apartment == null ? null : apartment.getName(),
                apartment == null ? null : apartment.getCityName(),
                apartment == null ? null : apartment.getDistrictName(),
                apartment == null ? null : apartment.getAddressDetail(), paymentOptions);
        AgentToolSupport.state(context).recordObservation("get_room_detail", result);
        return result;
    }

    public record RoomDetailResult(
            Long roomId,
            String roomNumber,
            BigDecimal rent,
            Long apartmentId,
            String apartment,
            String city,
            String district,
            String address,
            List<PaymentOption> paymentOptions) {
    }

    public record PaymentOption(String name, String payMonthCount, String description) {
    }
}
