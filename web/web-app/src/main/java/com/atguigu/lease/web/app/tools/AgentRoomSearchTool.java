package com.atguigu.lease.web.app.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AgentRoomSearchTool {

    private final RoomSearchTool roomSearchTool;

    public AgentRoomSearchTool(RoomSearchTool roomSearchTool) {
        this.roomSearchTool = roomSearchTool;
    }

    @Tool(name = "search_available_rooms",
            description = "Search real released rental rooms from the business database. Use this for budget or location requests.")
    public List<RoomSearchTool.RoomHit> search(
            @ToolParam(required = false, description = "City name") String city,
            @ToolParam(required = false, description = "District name") String district,
            @ToolParam(required = false, description = "Minimum monthly rent") BigDecimal minMonthlyRent,
            @ToolParam(required = false, description = "Maximum monthly rent") BigDecimal maxMonthlyRent,
            @ToolParam(required = false, description = "Maximum number of rooms, 1 to 5") Integer limit,
            ToolContext context) {
        int safeLimit = limit == null ? 5 : Math.max(1, Math.min(limit, 5));
        List<RoomSearchTool.RoomHit> rooms = roomSearchTool
                .searchRooms(city, district, minMonthlyRent, maxMonthlyRent)
                .stream().limit(safeLimit).toList();
        AgentToolSupport.state(context).recordObservation("search_available_rooms", rooms);
        return rooms;
    }
}
