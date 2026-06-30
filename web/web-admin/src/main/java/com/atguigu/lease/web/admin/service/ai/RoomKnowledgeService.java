package com.atguigu.lease.web.admin.service.ai;

/**
 * 房源知识库服务:把房源数据向量化并同步到 pgvector。
 */
public interface RoomKnowledgeService {

    /** 全量重建房源向量(namespace=rooms) */
    void reindexAll();

    /** 同步单个房源(删旧 + 重新入库) */
    void syncRoom(Long roomId);
}
