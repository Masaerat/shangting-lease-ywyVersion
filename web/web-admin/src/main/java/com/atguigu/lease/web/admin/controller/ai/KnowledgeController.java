package com.atguigu.lease.web.admin.controller.ai;

import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.web.admin.service.ai.DocumentKnowledgeService;
import com.atguigu.lease.web.admin.service.ai.KnowledgeManagementService;
import com.atguigu.lease.web.admin.vo.ai.KnowledgeDocQueryVo;
import com.atguigu.lease.web.admin.vo.ai.KnowledgeDocVo;
import com.atguigu.lease.web.admin.vo.ai.UploadResultVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "后台-AI知识库")
@RestController
@RequestMapping("/admin/ai/docs")
public class KnowledgeController {

    @Autowired private DocumentKnowledgeService documentKnowledgeService;
    @Autowired private KnowledgeManagementService knowledgeManagementService;

    @Operation(summary = "上传文档并解析入库")
    @PostMapping
    public Result<UploadResultVo> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "namespace", required = false) String namespace) {
        Long docId = documentKnowledgeService.uploadAndIngest(file, namespace);
        return Result.ok(new UploadResultVo(docId, "INDEXED"));
    }

    @Operation(summary = "根据条件分页查询文档列表")
    @GetMapping("page")
    public Result<IPage<KnowledgeDocVo>> page(@RequestParam long current, @RequestParam long size,
                                              KnowledgeDocQueryVo queryVo) {
        if (queryVo == null) queryVo = new KnowledgeDocQueryVo();
        Page<KnowledgeDocVo> page = new Page<>(current, size);
        return Result.ok(knowledgeManagementService.pageDocs(page, queryVo));
    }

    @Operation(summary = "删除文档(同时删向量与MinIO对象)")
    @DeleteMapping("{id}")
    public Result delete(@PathVariable Long id) {
        knowledgeManagementService.deleteDoc(id);
        return Result.ok();
    }

    @Operation(summary = "按 namespace 重建索引")
    @PostMapping("reindex")
    public Result reindex(@RequestParam String namespace) {
        knowledgeManagementService.reindexNamespace(namespace);
        return Result.ok();
    }
}
