package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.web.admin.service.ai.RoomKnowledgeService;
import com.atguigu.lease.web.admin.service.ai.event.RoomChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步监听房源变更,同步向量库。
 * 保存/更新 → 重新入库;删除 → 清理向量。
 */
@Component
public class RoomKnowledgeEventListener {

    @Autowired
    private RoomKnowledgeService roomKnowledgeService;

    @Async
    @EventListener
    public void onRoomChanged(RoomChangedEvent event) {
        if (event.getAction() == RoomChangedEvent.Action.DELETE) {
            roomKnowledgeService.deleteRoomVectors(event.getRoomId());
        } else {
            roomKnowledgeService.syncRoom(event.getRoomId());
        }
    }
}
