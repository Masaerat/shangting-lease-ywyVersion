package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.model.entity.AiKnowledgeDoc;
import com.atguigu.lease.web.admin.mapper.AiKnowledgeDocMapper;
import com.atguigu.lease.web.admin.service.ai.DocumentKnowledgeService;
import com.atguigu.lease.web.admin.service.ai.KnowledgeManagementService;
import com.atguigu.lease.web.admin.service.ai.RoomKnowledgeService;
import com.atguigu.lease.web.admin.vo.ai.KnowledgeDocQueryVo;
import com.atguigu.lease.web.admin.vo.ai.KnowledgeDocVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeManagementServiceImpl implements KnowledgeManagementService {

    private static final String NS_ROOMS = "rooms";

    @Autowired private AiKnowledgeDocMapper docMapper;
    @Autowired private VectorStore vectorStore;
    @Autowired private MinioClient minioClient;
    @Autowired private RoomKnowledgeService roomKnowledgeService;
    @Autowired private DocumentKnowledgeService documentKnowledgeService;

    @Override
    public IPage<KnowledgeDocVo> pageDocs(Page<KnowledgeDocVo> page, KnowledgeDocQueryVo q) {
        LambdaQueryWrapper<AiKnowledgeDoc> qw = new LambdaQueryWrapper<>();
        if (q != null) {
            qw.eq(q.getNamespace() != null && !q.getNamespace().isBlank(),
                    AiKnowledgeDoc::getNamespace, q.getNamespace());
            qw.eq(q.getStatus() != null && !q.getStatus().isBlank(),
                    AiKnowledgeDoc::getStatus, q.getStatus());
            qw.like(q.getKeyword() != null && !q.getKeyword().isBlank(),
                    AiKnowledgeDoc::getDocName, q.getKeyword());
        }
        qw.orderByDesc(AiKnowledgeDoc::getCreateTime);

        Page<AiKnowledgeDoc> entityPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<AiKnowledgeDoc> result = docMapper.selectPage(entityPage, qw);

        Page<KnowledgeDocVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<KnowledgeDocVo> records = result.getRecords().stream().map(this::toVo).toList();
        voPage.setRecords(records);
        return voPage;
    }

    private KnowledgeDocVo toVo(AiKnowledgeDoc doc) {
        KnowledgeDocVo vo = new KnowledgeDocVo();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }

    @Override
    public void deleteDoc(Long id) {
        AiKnowledgeDoc doc = docMapper.selectById(id);
        if (doc == null) return;
        // 删向量(按 docId 元数据过滤;docId 存 Long,过滤值也用 Long)
        vectorStore.delete(new FilterExpressionBuilder().eq("docId", id).build());
        // 删 MinIO 对象
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(doc.getMinioBucket()).object(doc.getMinioObjectKey()).build());
        } catch (Exception ignored) { /* 对象已不存在则忽略 */ }
        docMapper.deleteById(id);
    }

    @Override
    public void reindexNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) return;
        if (NS_ROOMS.equals(namespace)) {
            roomKnowledgeService.reindexAll();
            return;
        }
        // 文档 namespace:删该 namespace 全部向量,再逐个从 MinIO 读回重新 ingest
        vectorStore.delete(new FilterExpressionBuilder().eq("namespace", namespace).build());
        LambdaQueryWrapper<AiKnowledgeDoc> qw = new LambdaQueryWrapper<>();
        qw.eq(AiKnowledgeDoc::getNamespace, namespace);
        List<AiKnowledgeDoc> docs = docMapper.selectList(qw);
        for (AiKnowledgeDoc doc : docs) {
            documentKnowledgeService.ingest(doc.getId());
        }
    }
}
