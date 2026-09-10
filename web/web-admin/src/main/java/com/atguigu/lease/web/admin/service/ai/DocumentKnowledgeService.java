package com.atguigu.lease.web.admin.service.ai;

import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档管线:上传 → MinIO 落盘 → Tika 解析 → 分片 → 向量化。
 */
public interface DocumentKnowledgeService {

    /** 上传到 MinIO 并落元数据,返回 docId;随后异步解析入库 */
    Long uploadAndIngest(MultipartFile file, String namespace);

    /** 异步:解析已有 docId 的文档 → 分片 → 向量化 → 更新状态 */
    void ingest(Long docId);
}
