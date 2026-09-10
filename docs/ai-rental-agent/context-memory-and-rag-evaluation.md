# 分层上下文记忆与 RAG 评测说明

日期：2026-09-06；分支：`agentRag`。

## 1. 本轮解决了什么

本轮把两个过去只能“凭感觉说明”的能力变成了可检查、可测试的后端实现：

1. 对话不再超过固定轮数就直接丢弃最早信息，而是保存结构化用户条件、较早对话摘要和最近完整消息。
2. RAG 不再只看一次回答是否像是正确，而是拥有版本化数据集、离线指标、稳定排序和可直接调用的检索诊断接口。

本轮没有升级 Spring AI、没有引入 MCP、没有修改前端，也没有宣称尚未运行的真实向量准确率。

## 2. 上下文记忆设计

### 2.1 为什么不用“把全部历史交给模型总结”

纯模型摘要存在三个问题：可能漏掉预算和排除条件；每次摘要增加模型费用与延迟；摘要失败可能影响本轮主请求。因此本项目使用确定性分层记忆：

```text
用户当前问题
      ↑
最近 6 轮原文：负责指代、追问和短期连续性
      ↑
滚动摘要：负责保留较早对话过程
      ↑
结构化状态：负责预算、城市、区域等不可轻易丢失的约束
```

结构化状态目前可提取：`city`、`district`、`minRent`、`maxRent`、`rentMode`、`selectedRoomId`、`contactPhone`、`nearSubway`、`petFriendly`、`elevatorRequired`。它们来自用户自己的历史表达，只能帮助理解用户条件；实际房源价格、库存、合同和预约状态仍必须由业务工具核验。

### 2.2 Redis 数据格式

同一 Redis key 继续使用：

```text
ai:chat:history:{userId}:{conversationId}
```

新值是版本化 JSON：

```json
{
  "schemaVersion": 2,
  "revision": 8,
  "summary": "用户：最初希望靠近地铁；顾问：已给出两套候选……",
  "state": {
    "city": "上海",
    "district": "浦东新区",
    "maxRent": "3500",
    "rentMode": "WHOLE"
  },
  "recentMessages": [
    {"role": "user", "content": "第二套可以预约吗？"},
    {"role": "assistant", "content": "请补充到访时间和联系人信息。"}
  ]
}
```

`revision` 每成功保存一轮加一。服务仍可读取旧字符串数组和更早的换行格式，读取后会按新模型构建上下文。Redis 读取或写入失败时只丢失记忆能力，不会重新执行已经发生的 Agent 工具操作。

### 2.3 压缩规则

- 默认保留最近 6 轮原文。
- 最近原文默认最多 6000 字符。
- 注入 Agent 的记忆默认最多 8000 字符。
- 滚动摘要默认最多 2000 字符。
- 每条原始消息和每个摘要片段也有独立上限。
- 超限时按完整“用户 + 助手”轮次压缩，至少保留最新一轮。
- 摘要达到上限时保留开头和最新部分；最关键的用户条件另存在结构化状态中。
- 同一 JVM 使用会话条带锁，避免同会话并发请求在读改写时相互覆盖。多实例部署所需的 Redis CAS/Lua 属于下一阶段，不在本轮伪装成已经实现。

可调环境变量：

```text
AI_MEMORY_RECENT_TURNS=6
AI_MEMORY_MAX_RECENT_CHARS=6000
AI_MEMORY_MAX_CONTEXT_CHARS=8000
AI_MEMORY_SUMMARY_MAX_CHARS=2000
AI_MEMORY_SUMMARY_ITEM_MAX_CHARS=240
AI_MEMORY_MESSAGE_MAX_CHARS=2000
```

## 3. RAG 检索升级

### 3.1 检索模式现在表示真实命中

诊断结果的 `mode` 有四种：

| mode | 含义 |
|---|---|
| `HYBRID` | 向量和本地词法检索都实际返回了结果 |
| `VECTOR` | 只有向量检索返回结果 |
| `LOCAL` | 只有本地词法知识返回结果 |
| `EMPTY` | 两种检索都没有证据 |

以前 PGVector 调用成功但返回 0 条时也会显示 `HYBRID`，现在不会。向量服务异常会写降级日志，但不会把用户问题或密钥写进日志。

### 3.2 为什么使用 RRF

向量相似度与关键词分数不是同一种量纲，直接相加会让某一来源因为分数范围不同而天然占优。RRF（Reciprocal Rank Fusion）只使用每个来源内部的排名：

```text
score(document) = Σ weight(source) / (rrfK + rank(source, document))
```

本项目默认：

```text
rrfK=60
vectorWeight=1.0
lexicalWeight=0.8
categoryBoost=0.08
```

融合结果归一化到 0～1；同分时按稳定文档键排序，因此相同知识库和参数下的检索顺序可复现。分类只做小幅加分，不会代替知识证据。

### 3.3 直接检索诊断接口

重新构建并启动 web-app 后，Knife4j 会出现分组：`APP-AI知识检索评测`。

```http
GET /app/ai/rag/search?question=押金怎么退&limit=5
access-token: 登录接口返回的 token
```

返回内容包括：

- `originalQuery`：用户原始问题；
- `rewrittenQuery`：加入领域同义词后的查询；
- `mode`：本次到底使用了哪一种召回；
- `citations`：Top-K 的文档、章节、片段、版本和融合分数。

这个接口是只读的，并且位于现有登录拦截器保护的 `/app/**` 路径下。它适合在 Knife4j/Postman 中单独检查 RAG，而不用让聊天模型参与，从而快速判断问题发生在“检索层”还是“生成层”。

## 4. 离线评测数据集与指标

数据集位于：

```text
web/web-app/src/test/resources/ai/rag-evaluation-dataset.jsonl
```

V1 包含 30 条：24 条可回答问题和 6 条无答案问题。可回答问题覆盖押金、付款、预约、报修、退租、入住材料，每个主题有多种口语或同义表达。每条数据保存 `id`、`datasetVersion`、`paraphraseGroup`、`question`、`expectedCategory` 和 `answerable`。

当前实现的确定性指标：

- `HitRate@K`：正确主题是否出现在前 K 条；
- `MRR`：第一个正确结果平均排得多靠前；
- `abstentionAccuracy`：知识库没有答案时是否返回空证据；
- `paraphraseConsistency`：同义问题 Top-K 文档集合的 Jaccard 一致性；
- `modeCounts`：LOCAL、VECTOR、HYBRID、EMPTY 各出现多少次。

本轮实际执行的 V1 本地基线为：

```text
samples=30
answerable=24
unanswerable=6
HitRate@3=1.0
MRR=1.0
abstentionAccuracy=1.0
paraphraseConsistency=1.0
modeCounts={LOCAL=24, EMPTY=6}
```

这些数字只证明内置小型 Markdown 词法基线通过了固定数据集，绝不代表 Embedding-2 或 PGVector 达到 100%。当前运行环境检查时 `vector_store` 仍为 0 条，所以简历或面试中必须如实区分。

## 5. 如何运行评测

本机不安装 JDK 时可以直接使用 Docker Maven 镜像：

```powershell
docker run --rm `
  -v "${PWD}:/workspace" `
  -v "C:\Users\86187\.m2:/root/.m2" `
  -w /workspace `
  maven:3.9.9-eclipse-temurin-21 `
  mvn -B -pl web/web-app -am `
  '-Dtest=RentalKnowledgeEvaluationTest,HybridRentalKnowledgeServiceTest,ConversationMemoryServiceTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

查询当前向量数量：

```powershell
docker compose exec -T pgvector `
  psql -U lease -d lease_vec `
  -c "SELECT count(*) FROM vector_store;"
```

## 6. 如何进行真实向量评测

1. 启动 `web-admin`，通过其 Knife4j 的“后台-AI知识库”上传真实租赁制度文档。
2. 上传成功后检查 MySQL 文档状态为 `INDEXED`，再检查 PGVector 数量大于 0。
3. 固定知识文档版本，不要一边改语料一边比较参数。
4. 通过 `/app/ai/rag/search` 对数据集逐条查询，记录 Top-K 的 `chunkId/category/mode/score`。
5. 分别运行向量、词法、混合、查询改写和 RRF 版本，保存同一数据集上的指标。
6. 调优只使用开发集，最终结果使用保留测试集；否则会把“记住测试题”误当成泛化能力。

建议依次验证：文档质量 → 切片大小 → 查询改写 → 混合权重 → 元数据过滤 → 拒答阈值 → 精排。当前 `chunkSize=800`、`topK=5`、`similarityThreshold=0.75` 都只是基线，不能在没有对照数据时声称最优。

## 7. 面试表达

可以这样描述上下文升级：

> 原实现使用固定滑动窗口，早期约束会被直接删除。我把它升级为分层记忆：最近原文解决指代，滚动摘要控制上下文大小，结构化状态保存预算和区域等硬约束。压缩采用确定性规则，不增加模型调用和幻觉风险；旧缓存可迁移，缓存失败不会导致工具重复执行。

可以这样描述 RAG 升级：

> 我没有把一次看似正确的回答当作 RAG 有效，而是建立版本化黄金数据集，把检索和生成拆开评测。检索侧使用 HitRate@K、MRR、拒答准确率和同义问题一致性；融合侧使用 RRF 避免向量分数与词法分数不可比，并用稳定键保证结果可复现。报告明确区分 LOCAL 与 VECTOR，只有真实向量数据跑过对照实验后才声明提升。

仍需诚实说明的边界：目前真实向量库尚无已导入文档，因此已经完成的是评测工具和本地基线，不是最终 Embedding 准确率结论。

## 8. 本轮验证结果

以下结果均在 `agentRag` 工作区中使用 `maven:3.9.9-eclipse-temurin-21` Docker 镜像执行，不依赖宿主机安装 JDK：

- 新增能力定向测试：22 项，失败 0、错误 0、跳过 0。
- `web-app` 全量非集成测试：92 项，失败 0、错误 0、跳过 0。
- `web-admin` 知识切块、文档入库和房源入库测试：4 项，失败 0、错误 0、跳过 0。
- 根项目六模块编译：`model`、`common`、`web`、`web-admin`、`web-app` 全部成功。
- `git diff --check`：无空白错误；Windows 工作区仅有 Git 的 LF/CRLF 转换提示。

测试日志中的 Broker NACK、Redis unavailable 和未登录异常是对应容错/鉴权测试主动构造的输入，最终断言均通过。真实外部模型、真实 Embedding 质量和 PGVector 数据集仍未纳入上述离线数字。
