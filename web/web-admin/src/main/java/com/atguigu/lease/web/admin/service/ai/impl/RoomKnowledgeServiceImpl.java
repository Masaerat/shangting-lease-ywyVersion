package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.model.enums.ReleaseStatus;
import com.atguigu.lease.web.admin.service.ApartmentInfoService;
import com.atguigu.lease.web.admin.service.LeaseTermService;
import com.atguigu.lease.web.admin.service.RoomInfoService;
import com.atguigu.lease.web.admin.service.ai.RoomKnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomKnowledgeServiceImpl implements RoomKnowledgeService {

    private static final String NS_ROOMS = "rooms";

    @Autowired private VectorStore vectorStore;
    @Autowired private RoomInfoService roomInfoService;
    @Autowired private ApartmentInfoService apartmentInfoService;
    @Autowired private LeaseTermService leaseTermService;
    @Autowired private RagProperties ragProperties;

    public RoomKnowledgeServiceImpl() {}

    public RoomKnowledgeServiceImpl(VectorStore vectorStore, RoomInfoService roomInfoService,
                                    ApartmentInfoService apartmentInfoService, LeaseTermService leaseTermService,
                                    RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.roomInfoService = roomInfoService;
        this.apartmentInfoService = apartmentInfoService;
        this.leaseTermService = leaseTermService;
        this.ragProperties = ragProperties;
    }

    /**
     * 构造房源描述文档(纯函数,便于单测)。
     */
    public Document toDocument(RoomInfo room, ApartmentInfo apartment) {
        StringBuilder sb = new StringBuilder();
        if (apartment != null) {
            if (apartment.getName() != null) sb.append("公寓:").append(apartment.getName()).append("。");
            if (apartment.getProvinceName() != null) sb.append(apartment.getProvinceName());
            if (apartment.getCityName() != null) sb.append(apartment.getCityName());
            if (apartment.getDistrictName() != null) sb.append(apartment.getDistrictName());
            if (apartment.getAddressDetail() != null) sb.append(apartment.getAddressDetail());
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '。') sb.append("。");
        }
        if (room.getRoomNumber() != null) sb.append("房间号:").append(room.getRoomNumber()).append("。");
        if (room.getRent() != null) sb.append("月租金:").append(room.getRent()).append("元。");

        Map<String, Object> meta = new HashMap<>();
        meta.put("namespace", NS_ROOMS);
        meta.put("docType", "room");
        meta.put("roomRef", room.getId());
        meta.put("source", apartment == null ? "" : apartment.getName());
        return new Document(sb.toString(), meta);
    }

    @Override
    public void syncRoom(Long roomId) {
        if (roomId == null) return;
        RoomInfo room = roomInfoService.getById(roomId);
        if (room == null) return;
        // 先删该 roomRef 的旧向量
        Expression del = new FilterExpressionBuilder().eq("roomRef", String.valueOf(roomId)).build();
        vectorStore.delete(del);
        ApartmentInfo apt = room.getApartmentId() == null ? null : apartmentInfoService.getById(room.getApartmentId());
        vectorStore.add(List.of(toDocument(room, apt)));
    }

    @Override
    public void reindexAll() {
        // 删整个 rooms namespace 再全量灌入(已发布房源)
        Expression del = new FilterExpressionBuilder().eq("namespace", NS_ROOMS).build();
        vectorStore.delete(del);

        LambdaQueryWrapper<RoomInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(RoomInfo::getIsRelease, ReleaseStatus.RELEASED);
        List<RoomInfo> rooms = roomInfoService.list(qw);

        int batch = 100;
        for (int i = 0; i < rooms.size(); i += batch) {
            List<RoomInfo> sub = rooms.subList(i, Math.min(i + batch, rooms.size()));
            List<Document> docs = sub.stream().map(r -> {
                ApartmentInfo apt = r.getApartmentId() == null ? null : apartmentInfoService.getById(r.getApartmentId());
                return toDocument(r, apt);
            }).toList();
            vectorStore.add(docs);
        }
    }
}
