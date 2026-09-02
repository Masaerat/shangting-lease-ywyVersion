package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.common.minio.MinioProperties;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.model.entity.AiKnowledgeDoc;
import com.atguigu.lease.web.admin.mapper.AiKnowledgeDocMapper;
import com.atguigu.lease.web.admin.service.ai.DocumentKnowledgeService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentKnowledgeServiceImpl implements DocumentKnowledgeService {

    private static final String STATUS_INDEXED = "INDEXED";
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_FAILED = "FAILED";

    private final VectorStore vectorStore;
    private final AiKnowledgeDocMapper docMapper;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final RagProperties ragProperties;
    private final StructuredKnowledgeChunker chunker;

    public DocumentKnowledgeServiceImpl(VectorStore vectorStore, AiKnowledgeDocMapper docMapper,
                                        MinioClient minioClient, MinioProperties minioProperties,
                                        RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.docMapper = docMapper;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.ragProperties = ragProperties;
        this.chunker = new StructuredKnowledgeChunker(ragProperties);
    }

    /** 暴露给单测:按 RagProperties 构造分片器。 */
    public TokenTextSplitter buildSplitter() {
        return chunker.splitter();
    }

    @Override
    public Long uploadAndIngest(MultipartFile file, String namespace) {
        try {
            String objectKey = "ai-doc/" + UUID.randomUUID() + "/" + file.getOriginalFilename();
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectKey)
                        .stream(in, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            AiKnowledgeDoc doc = new AiKnowledgeDoc()
                    .setDocName(file.getOriginalFilename())
                    .setContentType(file.getContentType())
                    .setSizeBytes(file.getSize())
                    .setNamespace(namespace == null ? ragProperties.getNamespaceDefault() : namespace)
                    .setStatus(STATUS_UPLOADING)
                    .setMinioObjectKey(objectKey)
                    .setMinioBucket(minioProperties.getBucketName());
            docMapper.insert(doc);
            ingest(doc.getId());
            return doc.getId();
        } catch (Exception e) {
            throw new RuntimeException("文档上传失败: " + e.getMessage(), e);
        }
    }

    @Async
    @Override
    public void ingest(Long docId) {
        AiKnowledgeDoc doc = docMapper.selectById(docId);
        try {
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(doc.getMinioBucket()).object(doc.getMinioObjectKey()).build())) {
                byte[] bytes = in.readAllBytes();
                List<Document> raw = new TikaDocumentReader(new ByteArrayResource(bytes)).get();
                List<Document> chunks = chunker.split(doc, raw);
                vectorStore.delete(new FilterExpressionBuilder().eq("docId", docId).build());
                vectorStore.add(chunks);
                doc.setChunkCount(chunks.size());
                doc.setStatus(STATUS_INDEXED);
                doc.setErrorMessage(null);
            }
        } catch (Exception e) {
            doc.setStatus(STATUS_FAILED);
            doc.setErrorMessage(e.getMessage());
        }
        docMapper.updateById(doc);
    }
}
