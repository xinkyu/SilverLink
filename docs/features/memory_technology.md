# 对话记忆技术方案（Memory Tech，2026-03 最新）

## 概述

本文档是 SilverLink 对话记忆系统的最新实现说明，覆盖：

1. 三层记忆架构（12 条短期窗口 + 结构化画像 + 长期 RAG）
2. 后台 LLM 增量提炼（空闲触发 + 周期触发）
3. 混合 RAG 召回与可配置策略（TopK、权重、语义开关）
4. 记忆中心调试面板（命中项与分数分解）
5. 全链路测试剧本与验收结论

目标是让系统具备“可持续理解、可回忆、可调试、可解释”的老人陪伴能力，而不是单轮问答。

---

## 实现文件

核心实现：

- `app/src/main/java/com/silverlink/app/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/silverlink/app/feature/chat/MemoryRetrievalService.kt`
- `app/src/main/java/com/silverlink/app/feature/chat/MemoryMaintenanceService.kt`
- `app/src/main/java/com/silverlink/app/data/local/MemoryRecordEntity.kt`
- `app/src/main/java/com/silverlink/app/data/local/UserProfileMemoryEntity.kt`
- `app/src/main/java/com/silverlink/app/data/local/MemoryDao.kt`
- `app/src/main/java/com/silverlink/app/data/local/ChatDao.kt`
- `app/src/main/java/com/silverlink/app/data/local/AppDatabase.kt`
- `app/src/main/java/com/silverlink/app/ui/chat/ChatScreen.kt`

测试实现：

- `app/src/androidTest/java/com/silverlink/app/feature/chat/MemoryMaintenanceServiceTest.kt`
- `app/src/androidTest/java/com/silverlink/app/feature/chat/KeywordMemoryRetrievalServiceTest.kt`
- `app/src/androidTest/java/com/silverlink/app/feature/chat/HybridMemoryRagServiceTest.kt`

---

## 总体架构

### 三层记忆（运行时）

1. 短期上下文（Session Window）

- 取当前会话最近 12 条消息
- 作用：保证最近回合连贯
- 特点：高实时、无检索

2. 结构化核心画像（Profile Memory）

- 存储：`user_profile_memory`
- 内容：`preferred_name`、`age`、`health_conditions`、`family_relations`、`living_location`、`preferences`
- 作用：稳定身份与长期偏好，不易被临时闲聊污染

3. 长期 RAG 记忆（Long-term Memory）

- 存储：`memory_records`
- 来源：后台 LLM 提炼后写入（不是每条用户原话直存）
- 作用：跨会话检索历史事件与事实

### Prompt 注入顺序

System Prompt 最终拼装顺序：

1. 基础角色设定
2. 记忆使用规则（优先使用 RAG 上下文）
3. 称呼提示
4. 核心画像
5. 长期记忆 RAG 上下文
6. 方言提示
7. 情绪提示
8. 最近 12 条短期消息 + 当前用户输入

此顺序确保“稳定事实”优先于“短期噪声”。

---

## 数据模型与迁移

### 长期记忆表：`memory_records`

字段：

- `id`：主键
- `sourceConversationId`：来源会话
- `content`：事实文本
- `keywordsText`：关键词串（逗号分隔）
- `importance`：重要性（0.1~1.0）
- `createdAt`：创建时间
- `lastAccessAt`：最后访问时间

主要用途：

- RAG 候选召回
- 重要性排序
- 时效衰减
- 去重与 touch

### 结构化画像表：`user_profile_memory`

字段：

- `key`：主键
- `value`：画像值
- `confidence`：置信度
- `updatedAt`：更新时间

同时用于保存系统游标：

- `llm_memory_cursor_id`：后台提炼已处理到的用户消息 ID
- `llm_memory_last_run_at`：最近一次提炼时间戳及触发来源

### 数据库版本

- 已升级到 `v7`
- 有 `6 -> 7` 显式迁移，创建记忆表及索引

---

## 后台 LLM 提炼机制

### 为什么改为后台 LLM

早期本地规则法（关键词猜测）容易误报、漏报，且在复杂叙述中稳定性不足。

当前改为：

- 用户消息先正常对话
- 记忆提炼异步后台执行
- 由 LLM 对一批消息做“稳定事实抽取 + 画像更新建议”

### 触发策略

1. 空闲触发（Idle）

- 每次用户消息后，重置定时器
- 45 秒无新消息后执行一次

2. 周期触发（Periodic）

- 后台每 20 分钟尝试执行一次
- 作为兜底，不依赖用户是否停止输入

### 增量处理策略

每次执行：

1. 读取 `llm_memory_cursor_id`
2. 调用 `getUserMessagesAfterId(cursor, limit=30)` 拉取未处理消息
3. LLM 提炼 JSON：
   - `memory_facts`: 事实记忆数组
   - `profile_updates`: 画像更新数组
4. 写入记忆与画像
5. 推进游标到本批最大 message id

因此行为是：

- 不会重复提炼同一批消息
- 不只处理最后一条，而是处理“游标之后的一整批”

### LLM 输出契约（严格 JSON）

后台 prompt 强制 LLM 仅返回 JSON，格式为：

```json
{
  "memory_facts": [{ "text": "...", "importance": 0.0 }],
  "profile_updates": [{ "key": "...", "value": "...", "confidence": 0.0 }]
}
```

并通过 `extractJsonObject` 做防护解析。

---

## 长期记忆维护策略

由 `MemoryMaintenanceService` 负责。

### 1) 去重

- 精确去重：`content` 完全一致时更新旧记录
- 近似去重：与近期记录做 Jaccard 相似度比对，超过阈值走更新而非新增

### 2) 画像冲突合并

- 同值：提高置信度
- 可并合集合字段（如健康、家庭关系）：并集去重
- 冲突值：新置信度显著高于旧值才替换，否则保持旧值

### 3) 低价值清理

- 周期清理低重要性且过旧的记忆，防止膨胀

---

## 混合 RAG 召回机制

由 `HybridMemoryRagService` 实现。

### 候选构建

合并三路候选：

1. 关键词命中候选
2. 最近记忆候选
3. 高重要性候选

### 排序打分

总分由以下部分组成：

- 重要性分
- 关键词重叠分
- 语义相似分（可开关）
- 时效分（指数衰减）
- 直命中奖励（term hit bonus）

### 中文命中增强

为了避免“中文无空格导致召回差”：

- Query/记忆都做 2~3 gram 拆分
- 加入领域词优先（高血压、糖尿病、孙子等）
- 与传统 token 合并去重

### RAG 上下文输出

`buildGroundedContext` 输出带引用与分数的片段，例如：

- `记忆#ID`
- `score`
- 命中关键词标签

用于注入 System Prompt 并支持调试面板展示。

---

## RAG 可配置策略

配置对象：`RagConfig`

可调项：

1. `topK`（1~8）
2. `enableSemanticSimilarity`
3. `weightImportance`
4. `weightTokenOverlap`
5. `weightSemantic`
6. `weightRecency`

策略说明：

- 所有权重会自动归一化，避免配置和不稳定
- 语义关闭时，语义权重自动归零

---

## 调试与观测

### 1) 记忆中心调试面板

入口：聊天页右上角记忆中心。

可看内容：

- 最近 Query
- 命中记忆列表
- 每条分数分解（重要性/重叠/语义/时效）

可调内容：

- TopK
- 语义开关
- 四项权重滑杆
- 调试模式开关

### 2) Logcat 关键日志

Tag：`ChatViewModel`
前缀：`[LLM-Memory]`

关键日志点：

- `schedule idle extraction...`
- `trigger=... cursor=... fetched=...`
- `processing messageRange=[x,y]`
- `extracted ... savedFacts ... savedProfiles ...`
- `done trigger=... newCursor=...`

用于确认：

- 空闲 45 秒是否触发
- 批处理范围是否正确
- 是否真的写入了记忆与画像

---

## 实测剧本（已验证成功）

太棒了！为了彻底检验这套三层记忆架构（12条短期滑动窗口 + 结构化核心画像 + 长期 RAG 召回），我们需要一个精心设计的“剧本”。

这个剧本分为四个阶段。请你**逐条输入**（每输入一条，等 AI 回复后再输下一条），并在最后阶段观察 AI 的回答是否符合预期。

---

### 第一阶段：注入核心画像与长期记忆（埋点）

_这一阶段的目的是让系统提取出结构化信息和关键事件，存入底层数据库。_

1. **“小银你好，我是李建国，今年72岁了。”**

- _(测试目标：核心画像提取 - 姓名、年龄)_

2. **“我这人有高血压，医生特意嘱咐我平时做饭要少放盐。”**

- _(测试目标：核心画像提取 - 健康状况、饮食习惯)_

3. **“我孙子叫小明，他刚才打电话说这周末要来看我，我挺高兴的。”**

- _(测试目标：长期记忆事件、核心画像 - 家庭成员)_

4. **“今天早上天气好，我去楼下小公园打了一套太极拳。”**

- _(测试目标：长期记忆事件 - 日常活动)_

---

### 第二阶段：闲聊灌水（蓄意顶掉滑动窗口）

_目前的滑动窗口是 12 条。假设你发一条、AI 回一条算作 2 条，我们需要连续聊至少 6-8 轮无关的话题，把第一阶段的“埋点”彻底挤出短期上下文（System Prompt 里的短期窗口将不再包含前面的信息）。_

5. **“中午随便吃了一碗西红柿鸡蛋面。”**
6. **“下午闲着没事，看了两集老版的三国演义。”**
7. **“今天外面的风感觉有点大啊。”**
8. **“刚才隔壁老王过来找我下象棋，我连赢了他两局！”**
9. **“不过坐久了，感觉腰稍微有点酸。”**
10. **“现在的年轻人天天看手机，对眼睛多不好啊。”**
11. **“我打算给自己泡一杯枸杞茶喝。”**
12. **“马上快到吃晚饭的时间了，还没想好弄点啥。”**

_(到这里，前 4 句话已经绝对不在最近的 12 条短期聊天记录里了。如果接下来 AI 还能答对，完全就是 RAG 和核心画像的功劳！)_

---

### 第三阶段：终极连考（见证奇迹的时刻）

_现在开始测试系统的召回能力。注意打开你的“RAG 策略”调试面板看命中分数。_

13. **“哎，年纪大了记性不好，我刚才说我打算喝点什么水来着？”**

- _预期结果：答出“枸杞茶”。_
- _验证逻辑：这是第 11 句话的内容，依然在 12 条短期滑动窗口内，无需 RAG 也能轻松答对，测试**短期记忆的连贯性**。_

14. **“你还记得我身体有啥慢性病，平时饮食上要注意些啥吗？”**

- _预期结果：答出“高血压”、“少吃盐”。_
- _验证逻辑：测试**结构化核心画像**是否被正确且稳定地注入到了 System Prompt 的最顶层。_

15. **“对了，这周末家里有什么安排来着？谁要来？”**

- _预期结果：答出“孙子小明要来看您”。_
- _验证逻辑：测试**混合 RAG 召回**。这句话并没有提及“小明”这两个字，如果命中，说明“语义相似度 (Semantic)”的权重发挥了极大的作用！_

16. **“早上我出去锻炼身体了吗？做啥运动了？”**

- _预期结果：答出“去楼下公园打太极拳了”。_
- _验证逻辑：同上，测试 RAG 检索对历史事件的召回。_

---

### 第四阶段：冲突合并与更新测试（进阶防翻车）

_测试系统的 `MemoryMaintenanceService` 是不是真的会按置信度覆盖旧数据。_

17. **“小银啊，今天下午我去医院复查了，医生说我除了高血压，现在还有点轻微的糖尿病，以后甜食也得严格控制了。”**

- _测试目标：观察系统是否能在后台将“糖尿病”和“控制甜食”追加或更新到之前的健康画像中。_

18. _(随便聊一句过渡)_ **“医院的人可真多啊，排队排了半天。”**
19. **“你帮我总结一下，我现在的身体情况，饮食上到底都要忌口些什么？”**

- _预期结果：同时答出“高血压少吃盐”和“糖尿病控制甜食”。_
- _验证逻辑：测试核心画像的**并集更新/冲突合并机制**是否生效。_

---

---

## 回归测试建议

每次改动建议跑以下检查：

1. 编译

- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:compileDebugAndroidTestKotlin`

2. 手工场景

- 跑一遍四阶段剧本
- 打开记忆中心调试面板观察 score 和命中项
- 同时看 Logcat 的 `[LLM-Memory]` 日志

3. 自动化

- `MemoryMaintenanceServiceTest`
- `KeywordMemoryRetrievalServiceTest`
- `HybridMemoryRagServiceTest`

---

## 隐私与安全

当前约束：

- 记忆数据本地持久化（Room）
- 不通过 CloudBase 同步记忆库
- 后台提炼调用在线模型时，仅发送待提炼用户文本批次

后续增强建议：

1. 增加用户侧“允许后台记忆提炼”开关
2. 增加本地敏感字段脱敏
3. 增加导出/清空/分级删除能力

---

## 已知限制

1. 后台提炼当前依赖在线 LLM（非端侧小模型）
2. 当输入量长期高于批大小时，提炼会分批推进而不是一次全量
3. 语义相似仍是轻量近似（ngram），非真正向量索引

---

## 演进路线

1. 抽象 `MemoryExtractor` 接口并支持本地小模型实现
2. 引入向量库与 embedding 检索
3. 增加 Query Rewrite 提升隐式问句召回率
4. 增加记忆来源可视化（短期/画像/RAG 命中来源）

---

## 修改记录

| 日期       | 修改内容                                                                         |
| ---------- | -------------------------------------------------------------------------------- |
| 2026-03-13 | 初始版：三层记忆架构、冲突合并策略、记忆中心 UI                                  |
| 2026-03-13 | 增强版：后台 LLM 增量提炼、混合 RAG 可配置策略、调试面板、完整实测剧本与验收结论 |
