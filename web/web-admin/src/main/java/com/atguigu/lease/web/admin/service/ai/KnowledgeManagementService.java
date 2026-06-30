package com.atguigu.lease.web.admin.service.ai;

import com.atguigu.lease.web.admin.vo.ai.KnowledgeDocQueryVo;
import com.atguigu.lease.web.admin.vo.ai.KnowledgeDocVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 知识库管理:文档分页 / 删除(向量+MinIO+元数据)/ 按 namespace 重建索引。
 */
public interface KnowledgeManagementService {

    /** 分页查询文档元数据 */
    IPage<KnowledgeDocVo> pageDocs(Page<KnowledgeDocVo> page, KnowledgeDocQueryVo queryVo);

    /** 删除文档:向量 + MinIO 对象 + 元数据 */
    void deleteDoc(Long id);

    /** 按 namespace 重建索引(rooms 走 RoomKnowledgeService,其余重新 ingest 该 namespace 全部文档) */
    void reindexNamespace(String namespace);
}
