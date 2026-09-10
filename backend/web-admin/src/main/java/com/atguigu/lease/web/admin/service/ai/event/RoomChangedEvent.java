package com.atguigu.lease.web.admin.service.ai.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 房源变更事件:保存/更新或删除时发布,异步同步向量库。
 */
@Getter
public class RoomChangedEvent extends ApplicationEvent {

    public enum Action { SAVE, DELETE }

    private final Long roomId;
    private final Action action;

    public RoomChangedEvent(Object source, Long roomId, Action action) {
        super(source);
        this.roomId = roomId;
        this.action = action;
    }
}
