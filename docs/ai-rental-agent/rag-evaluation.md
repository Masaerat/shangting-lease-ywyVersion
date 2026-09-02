# RAG 评测报告

更新时间：2026-09-02

## 1. 评测目标

RAG 质量与最终模型文案质量分开评估。本报告先验证“检索是否把正确类别的证据排到前面”，不使用一次看起来合理的模型回答代替检索指标。

## 2. 数据集

离线回归集共 10 条，五个类别各 2 条，并加入业务同义表达。

| # | 问题 | 期望分类 |
| --- | --- | --- |
| 1 | 押金什么时候退？ | DEPOSIT |
| 2 | 合同结束后保证金怎么处理？ | DEPOSIT |
| 3 | 这个房子支持月付吗？ | PAYMENT |
| 4 | 租金能不能按三个月支付？ | PAYMENT |
| 5 | 预约看房需要填写什么？ | APPOINTMENT |
| 6 | 到访之前要先确认吗？ | APPOINTMENT |
| 7 | 入住后水管故障怎么处理？ | REPAIR |
| 8 | 普通维修如何报修？ | REPAIR |
| 9 | 退租需要走哪些流程？ | CHECKOUT |
| 10 | 钥匙交还和费用结算怎么做？ | CHECKOUT |

每条取 `topK=3`，要求第一条引用分类正确、来源为 `rag-knowledge.md` 且摘要非空。

## 3. 指标

- `HitRate@1`：第一条引用分类正确的问题数 / 总问题数。
- `MRR`：每条问题正确分类首次出现名次的倒数，再取平均。
- `Traceability`：引用是否包含来源和可展示摘要。
- `Faithfulness`：最终回答是否只陈述引用或业务工具支持的内容，需要在线模型结果人工检查。

## 4. 本轮结果

| 检索路径 | HitRate@1 | MRR | Traceability | 状态 |
| --- | ---: | ---: | --- | --- |
| LOCAL Markdown fallback | 1.00 (10/10) | 1.00 | 10/10 | 自动化通过 |
| PGvector + LOCAL hybrid | 未测 | 未测 | DTO/单测已覆盖 | 待在线评测 |
| 最终模型回答 Faithfulness | 未测 | 不适用 | 不适用 | 待真实模型人工评审 |

LOCAL 指标只证明无向量服务时的确定性兜底能力，不能外推为向量召回质量。

## 5. 可复现命令

```powershell
.\mvnw.cmd -pl web/web-app -am '-Dtest=RentalKnowledgeEvaluationTest,HybridRentalKnowledgeServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## 6. Hybrid 排序公式

当前排序分数由三部分组成：

```text
score = vectorScore
      + 0.25 (分类匹配)
      + min(0.20, 命中领域词数量 * 0.04)
```

本地候选基础分是 0.45。最终分数截断到 1.0，并按稳定 `chunkId` 去重。这个方案简单但可解释，适合 Demo；不是训练得到的 Cross-Encoder reranker。

## 7. 在线评测如何补齐

在 PGvector 与 Embedding 可用时，应使用同一数据集并记录：改写查询、topK 的 chunkId/文档/章节/版本/分数、正确证据首次出现名次，以及向量-only、关键词-only、hybrid 三组对照。最终回答需做 supported/unsupported 句子级人工判断。

建议后续扩充到至少 50 条，并加入无答案、跨章节、错别字、口语、省略上下文和恶意指令场景。数据集版本必须固定，否则指标不可比较。

## 8. 本轮根据评测做的改进

- 将“押金与付款”拆成两个章节，避免 PAYMENT 被统一标成 DEPOSIT。
- 增加保证金/退押、租金、到访、故障、钥匙等同义词。
- 保留来源、章节、版本和摘要，保证客户端能展示证据。
