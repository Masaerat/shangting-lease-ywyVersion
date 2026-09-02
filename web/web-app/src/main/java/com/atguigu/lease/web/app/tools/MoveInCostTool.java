package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.model.entity.PaymentType;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.app.service.PaymentTypeService;
import com.atguigu.lease.web.app.service.RoomInfoService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MoveInCostTool {

    private static final Pattern NUMBER = Pattern.compile("(\\d+)");
    private static final Pattern DEPOSIT = Pattern.compile("押([一二三四五]|\\d+)");

    private final RoomInfoService roomInfoService;
    private final PaymentTypeService paymentTypeService;

    public MoveInCostTool(RoomInfoService roomInfoService, PaymentTypeService paymentTypeService) {
        this.roomInfoService = roomInfoService;
        this.paymentTypeService = paymentTypeService;
    }

    @Tool(name = "calculate_move_in_cost",
            description = "Calculate first payment from the current room rent and a supported payment option. This is an estimate, not a contract quote.")
    public MoveInCostResult calculate(
            @ToolParam(description = "Room primary key returned by search_available_rooms") Long roomId,
            @ToolParam(required = false, description = "Preferred payment option name, for example 月付 or 季付") String paymentName,
            ToolContext context) {
        RoomInfo room = roomInfoService.getById(roomId);
        if (room == null || room.getIsRelease() != ReleaseStatus.RELEASED || room.getRent() == null) {
            throw new IllegalArgumentException("Room is unavailable: " + roomId);
        }
        List<PaymentType> options = paymentTypeService.listByRoomId(roomId);
        PaymentType selected = options.stream()
                .filter(item -> paymentName != null && paymentName.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> options.stream().findFirst().orElse(null));
        int payMonths = selected == null ? 1 : positiveNumber(selected.getPayMonthCount(), 1);
        int depositMonths = selected == null ? 1 : depositMonths(selected.getAdditionalInfo());
        BigDecimal rentPayment = room.getRent().multiply(BigDecimal.valueOf(payMonths));
        BigDecimal deposit = room.getRent().multiply(BigDecimal.valueOf(depositMonths));
        MoveInCostResult result = new MoveInCostResult(
                roomId, room.getRent(), selected == null ? "未配置，按月付估算" : selected.getName(),
                payMonths, depositMonths, rentPayment, deposit, rentPayment.add(deposit),
                "估算不含水电、服务费等杂费，最终以合同和房源详情为准");
        AgentToolSupport.state(context).recordObservation("calculate_move_in_cost", result);
        return result;
    }

    private int positiveNumber(String text, int fallback) {
        if (text == null) return fallback;
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) return fallback;
        return Math.max(1, Integer.parseInt(matcher.group(1)));
    }

    private int depositMonths(String text) {
        if (text == null) return 1;
        Matcher matcher = DEPOSIT.matcher(text);
        if (!matcher.find()) return 1;
        return switch (matcher.group(1)) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            default -> Math.max(1, Integer.parseInt(matcher.group(1)));
        };
    }

    public record MoveInCostResult(
            Long roomId,
            BigDecimal monthlyRent,
            String paymentName,
            int rentMonths,
            int depositMonths,
            BigDecimal rentPayment,
            BigDecimal deposit,
            BigDecimal estimatedFirstPayment,
            String disclaimer) {
    }
}
