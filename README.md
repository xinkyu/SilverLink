# 🌟 SilverLink（银龄守护）

面向老年人与家属的双端 AI 陪伴与辅助应用：**老人端**更易用，**家人端**可远程看护与同步信息。项目以 Android 为主端，后端使用云函数承载轻量业务。

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose"/>
</p>

## 🎯 核心功能

- **AI 陪伴对话**：支持按住说话与实时全双工对话模式
- **AI 视觉识别**：拍照识别药品名称、剂量与服药时间
- **AI 用药核对**：实时相机预览，稳住画面自动核对药品名称、剂量与服药时间
- **智能用药提醒**：多时段提醒、全屏提示、每日重置
- **记忆时光机**：家人上传照片，AI 自动描述生成记忆小游戏
- **认知评估**：基于照片的记忆问答，双端可查看评估报告
- **安全跌倒检测**：多重传感器算法检测跌倒并自动报警
- **实时位置追踪**：家人端实时查看长辈位置与轨迹
- **主动关怀交互**：检测长辈活动状态，长时间无响应自动通知家人
- **家属远程同步**：配对码绑定老人端与家人端
- **情绪与用药记录**：云端记录与家属端查看

## 📚 功能文档 (Feature Documentation)

- [🤖 聊天与 AI 陪伴 (Chat & AI Companion)](docs/features/chat_companion.md)
- [📞 实时对话模式 (Real-time Conversation)](docs/features/realtime_conversation_mode.md)
- [🛡️ 安全与跌倒检测 (Safety & Fall Detection)](docs/features/safety_alert.md)
- [📍 位置追踪 (Location Tracking)](docs/features/location_tracking.md)
- [📷 记忆时光机 (Memory Time Machine)](docs/features/memory_time_machine.md)
- [💊 健康与用药提醒 (Health & Medication)](docs/features/health_management.md)
- [🔗 设备配对与启动 (Device Pairing)](docs/features/device_pairing.md)
- [🧠 认知与情绪追踪 (Cognitive & Mood)](docs/features/cognitive_mood.md)
- [📑 功能总览 (Summary)](docs/features/summary.md)

## 📖 详细功能介绍（基于代码实现）

### 1) 双端角色与配对

- **家人端配置向导**：填写长辈称呼与可选画像信息，一键生成 6 位配对码，并同步到云端。
- **二维码分享**：家人端生成二维码（包含配对码与长辈称呼信息），便于面对面扫码或口头输入。
- **长辈端激活**：输入 6 位配对码完成激活；优先云端校验，失败时可回退本地校验（便于同设备测试）。
- **配对关系校验**：家人端访问长辈数据时会携带设备 ID 验证配对关系，保证数据隔离。

### 2) AI 陪伴对话（老人端）

- **语音对话全流程**：录音 → 语音识别 → 文字对话 → 语音播报（TTS），面向老人简化交互。
- **声音复刻**：支持录制家属声音，AI 训练后使用家人的声音回答长辈，更具亲切感。
- **情绪陪伴**：对话文本自动识别情绪（AI 优先，关键词兜底），驱动情绪徽章与语气响应。
- **安全与关怀**：系统提示包含“身体不适建议就医/联系家人”“识别诈骗风险提醒”等安全策略。
- **会话管理**：支持新建/切换/删除会话，自动生成会话标题并持久化本地聊天记录。
- **实时对话模式**：模拟打电话体验，支持双向实时交流。具备 VAD 自动检测、智能打断与回声消除功能，提供更自然的陪伴体验。

### 3) 智能用药管理与提醒（老人端）

- **本地药品库**：新增/编辑/删除药品，支持多时间点服药（如 08:00/12:00/18:00）。
- **精准闹钟**：为每个时间点设置独立提醒；到点全屏弹窗 + 高优先级通知。
- **稍后提醒**：支持一键“稍后提醒”，默认延后 10 分钟再次唤醒。
- **打卡与历史**：点击确认即记录“已服用”，同时写入本地历史并同步云端。
- **每日重置**：每日凌晨自动重置当日服药状态，保证当天记录准确。

### 4) AI 药品识别（老人端）

- **实时预览**：CameraX 实时画面分析，稳住 0.5 秒自动截帧识别，无需按快门。
- **结构化结果**：识别结果直接生成可编辑药品条目，减少输入成本。
- **容错策略**：识别失败会给出提示并允许手动补录。

### 5) AI 用药核对（老人端）

- **实时取帧**：基于 CameraX 预览流分析，画面稳定后自动截取当前帧。
- **智能匹配**：将识别出的药品与当前时间窗口内的计划药品进行匹配校验。
- **语音播报**：正确/错误/不在时间段等结果以语音与文案提示。
- **静默记录**：匹配成功后自动写入当次服药记录并同步云端。

### 6) 健康记录与可视化（老人端）

- **多维时间范围**：日/周/月/年切换，配合日期选择器查看历史。
- **情绪时间轴**：当天展示情绪时间轴，可点击查看对话摘要与详情。
- **情绪分布分析**：周/月/年展示情绪分布与 AI 摘要分析。
- **用药状态卡**：按天展示时间点完成情况；按周/月/年展示汇总与缺失统计。

### 7) 家人端健康监护

- **健康仪表盘**：统一界面展示长辈情绪与用药记录，支持日/周/月/年切换。
- **趋势与分析**：情绪分布与 AI 文字分析帮助家属快速了解状态变化。
- **远程加药**：家人端可为长辈添加药品与服药时间，老人端自动同步并设置闹钟。
- **位置追踪**：实时查看长辈的地理位置，支持反地理编码（显示具体街道地址）。
- **异常提示**：未配对或加载失败时提供友好提示与重试机制。

### 8) 记忆时光机（双端）

- **家人端上传**：选择照片并填写描述（人物、地点、时间、事件），支持直传云存储。
- **AI 描述生成**：上传时自动调用视觉模型生成照片描述，补充用户未填写的信息。
- **老人端照片库**：瀑布流展示照片，点击查看大图与详情，支持自然语言搜索。
- **记忆小游戏**：基于照片生成问答（"这是谁？""这是在哪里？"），语音作答。
- **增量同步**：老人端定期拉取新照片，保持双端数据一致。

### 9) 认知评估（双端）

- **答题记录**：记忆小游戏的每次作答（正确/错误、用时）自动上传云端。
- **统计报告**：按日/周/月统计总题数、正确率、平均用时。
- **趋势分析**：对比前后时间段正确率，判断"进步中/保持稳定/需关注"。
- **双端查看**：老人端和家人端均可在健康记录中查看认知评估报告。

### 10) 主动关怀与安全 (老人端)

- **跌倒检测**：监测加速度剧烈变化，结合 ML 算法识别跌倒动作；自动触发红屏警报、播放警笛并发送远程通知。
- **活动检测**：通过步数计数器和加速度计检测老人是否在活动。
- **定时唤醒**：每 3 小时语音问候老人（使用 AI 语音合成个性化内容）。
- **无响应告警**：连续两次唤醒无响应时，自动向家人端发送告警通知。
- **光线感知**：夜间（低光环境）自动暂停唤醒，避免打扰休息。
- **前台服务**：保持后台运行，即使应用被杀也能继续监护。

### 11) 云端同步与容错

- **轻量云函数**：配对、药品、用药记录、情绪记录均通过云函数读写云数据库。
- **本地优先**：云端异常不阻塞本地功能，核心流程可离线继续。
- **缓存与性能优化**：健康记录使用内存缓存与后台刷新策略，提升列表与图表加载速度。

## 🧩 技术栈

| 层级   | 技术                                  | 说明                              |
| ------ | ------------------------------------- | --------------------------------- |
| 客户端 | Kotlin / Jetpack Compose / Material 3 | 大字体与高对比度界面、现代化 UI   |
| 数据   | Room                                  | 本地持久化（用药/记录）           |
| 网络   | Retrofit + OkHttp                     | REST 调用云函数与 AI 服务         |
| 任务   | WorkManager                           | 每日重置与后台任务                |
| 相机   | CameraX                               | 实时预览与帧分析                  |
| 序列化 | kotlinx-serialization                 | JSON 编解码                       |
| 工具   | ZXing                                 | 配对二维码生成                    |
| AI     | Alibaba DashScope                     | Qwen 对话 / Qwen2-Audio / Qwen-VL |
| 后端   | 腾讯云 CloudBase                      | Node.js 云函数 + 云数据库         |

## 🔌 AI 能力

- **Qwen 对话**：陪伴聊天与日常问答
- **Qwen2-Audio**：语音识别（按住说话 → ASR）
- **Qwen-VL**：药品包装识别（名称/剂量/时间）

## 📂 项目结构

```
app/src/main/java/com/silverlink/app/
├── data/
│   ├── local/          # Room 数据库
│   └── remote/         # Retrofit / CloudBase
├── feature/
│   ├── chat/           # 语音与对话
│   └── reminder/       # 用药提醒
└── ui/
    ├── chat/           # 聊天界面
    ├── reminder/       # 提醒界面
    ├── onboarding/     # 双端配对与激活
    └── theme/          # 主题配置

cloud-functions/
├── pairing-create/          # 创建配对码
├── pairing-verify/          # 验证配对码
├── pairing-get-elder/       # 获取已配对老人
├── medication-add/          # 添加药品
├── medication-update/       # 更新药品
├── medication-delete/       # 删除药品
├── medication-list/         # 获取药品列表
├── medication-log/          # 记录服药
├── medication-query/        # 查询服药记录
├── alert-send/              # 发送告警
├── alert-query/             # 查询告警
├── alert-dismiss/           # 确认告警
├── mood-log/                # 记录情绪
├── mood-query/              # 查询情绪记录
├── memory-photo-credentials/# 获取照片上传凭证
├── memory-photo-save/       # 保存照片元数据
├── memory-photo-list/       # 获取照片列表
├── memory-photo-search/     # 搜索照片
├── cognitive-log/           # 记录认知评估结果
└── cognitive-report/        # 获取认知评估报告
```

## 🚀 快速开始

### 前置条件

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34

### 1) 配置 API Key 与 CloudBase 地址

在项目根目录的 `local.properties` 文件中添加以下配置：

```properties
# 阿里云 DashScope API Key（用于 AI 对话、语音识别、视觉识别）
QWEN_API_KEY=你的DashScope_API_Key

# 腾讯云 CloudBase 云函数访问地址
CLOUDBASE_URL=https://你的环境ID-你的appid.ap-shanghai.app.tcloudbase.com/
```

### 2) 运行 Android 客户端

1. 打开 Android Studio → **Open** → 选择项目根目录 SilverLink
2. 等待 Gradle Sync 完成（首次可能较慢）
3. 连接真机或启动模拟器
4. 选择运行配置 **app** → **Run**

首次运行时：

- Android 13+ 会请求通知权限（用药提醒）
- 语音输入会请求录音权限
- 拍照识别会请求相机权限

### 3) 部署云函数（可选，但建议）

参考 [cloud-functions/README.md](cloud-functions/README.md) 完成 CloudBase 环境创建、数据库集合与云函数部署。

## 🔐 安全与隐私

- API Key 与 CloudBase 地址通过 `local.properties` 配置，构建时自动注入 `BuildConfig`，源码中不包含敏感信息
- 上传云端的用药/情绪日志建议进行脱敏与最小化字段存储

## 📄 License

MIT License

---

<p align="center">用爱连接，让科技温暖每一位老人 ❤️</p>
