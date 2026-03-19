# SilverLink / 银龄守护

SilverLink 是一套面向老人看护场景的多端系统，覆盖老人手机端、家属手机端、Wear OS 手表端，以及基于腾讯云 CloudBase 的轻量后端。项目核心目标不是单点功能演示，而是把陪伴、用药、安全、位置、记忆训练和健康数据整合成一条完整链路。

当前仓库包含：

- `app`：Android 主应用，按角色切换老人端与家属端
- `wear`：Wear OS 表端应用
- `shared`：手机与手表共享协议、跌倒检测公共逻辑
- `oppo-sdk-bridge`：OPPO 健康 SDK 桥接层，支持真实 SDK 与 Mock 回退
- `cloud-functions`：CloudBase 云函数，承载配对、用药、情绪、记忆相册、认知评估、位置、告警、音频上传等服务

## 项目特性

### 老人端

- AI 语音陪伴：录音、语音识别、对话生成、TTS 播报全链路可用
- 语音情绪识别：本地 ONNX 情绪模型与文本情绪兜底结合
- 长期记忆机制：聊天内容会抽取长期记忆和用户画像，用于后续 RAG 检索
- 声音复刻：支持上传家属音频，使用复刻音色进行播报
- 智能用药提醒：多时段闹钟、全屏提醒、稍后提醒、每日重置
- AI 药品识别与核对：CameraX 实时取帧识别药品，并和当前计划用药做匹配
- 记忆相册与问答：同步家属上传照片，支持大图浏览、记忆小游戏
- 主动关怀：前台服务结合传感器做久坐/无响应提醒
- 跌倒守护：本地模型与传感器结合识别跌倒并触发紧急流程
- 位置共享：前台定位服务定时上传位置与地址

### 家属端

- 健康记录总览：日/周/月/年查看情绪、用药、认知、健康指标
- 位置守护：查看长辈最新位置与近两小时轨迹
- 记忆库：上传老照片、补充描述、同步给老人端
- 远程加药与用药历史：同步管理药品和服药记录
- 告警接收：接收无响应、跌倒等风险告警
- 配对与激活：生成配对码和二维码，绑定长辈设备

### Wear OS 表端

- 健康首页：展示心率和快捷入口
- SOS 页面：手表侧快速求助
- 用药提醒页：接收并展示提醒
- 睡眠与心率页面：承载表端健康视图
- 跌倒检测与健康监测服务：后台传感器采集与同步
- 开机自启与本地告警 Activity

### 健康数据接入

- 已接入 OPPO 健康 SDK 桥接能力
- 支持读取步数、活动、心率、血氧、压力、睡眠、血压、体重等数据
- App 侧通过 `OppoHealthSdkManager` 做授权、初始化、聚合和错误处理
- 默认支持 Mock 回退，便于在无真实健康 App 或无真机环境下开发

## 代码架构

### Android 主端

- UI：Jetpack Compose + Material 3
- 本地存储：Room
- 网络：Retrofit + OkHttp
- 后台任务：WorkManager
- 相机：CameraX
- 模型推理：ONNX Runtime
- 定位：Google Play Services Location

### AI 与后端

- 大模型接口：阿里云 DashScope / Qwen 系列
- 语音、视觉、对话统一由 Android 端调用远程 API
- 云端后端：腾讯云 CloudBase HTTP 云函数
- 媒体上传：通过云函数换取凭证后直传 COS

### 多模块职责

```text
app/               Android 主应用（老人端 + 家属端）
wear/              Wear OS 应用
shared/            手表通信协议、跌倒检测公共逻辑
oppo-sdk-bridge/   OPPO 健康 SDK 桥接与 Mock
cloud-functions/   CloudBase 云函数
docs/              功能说明、SDK 接入文档、研发记录
scripts/           模型训练与数据分析脚本
```

## 主要代码入口

- `app/src/main/java/com/silverlink/app/MainActivity.kt`
  负责应用入口、通知权限、老人端前台服务启动
- `app/src/main/java/com/silverlink/app/ui/MainScreen.kt`
  按角色切换老人端与家属端主导航
- `app/src/main/java/com/silverlink/app/ui/chat/ChatViewModel.kt`
  负责聊天、情绪、TTS、语音命令、记忆抽取与 RAG 相关主流程
- `app/src/main/java/com/silverlink/app/data/remote/CloudBaseService.kt`
  统一封装云函数、直传 COS、位置、声音上传等远程能力
- `app/src/main/java/com/silverlink/app/feature/health/OppoHealthSdkManager.kt`
  OPPO 健康授权、聚合和 Dashboard 数据入口
- `app/src/main/java/com/silverlink/app/feature/location/LocationTrackingService.kt`
  老人端前台定位与上传服务
- `app/src/main/java/com/silverlink/app/feature/watch/WatchConnectionService.kt`
  手机侧与手表通信服务
- `wear/src/main/java/com/silverlink/wear/ui/screens/WatchNavigation.kt`
  表端导航入口

## 环境要求

- Android Studio 较新版本
- JDK 17
- Android SDK 34
- 最低 Android 版本：`minSdk 26`
- Kotlin 2.0.0
- AGP 8.6.0

## 本地配置

项目通过根目录 `local.properties` 注入运行参数。常用项如下：

```properties
# Qwen / DashScope
QWEN_API_KEY=your_dashscope_key

# CloudBase HTTP 服务根地址
CLOUDBASE_URL=https://your-env-id.ap-shanghai.app.tcloudbase.com/

# OPPO 健康 SDK：开发阶段可先走 Mock
FORCE_MOCK_HEALTH_DATA=true

# 可选：发布签名
RELEASE_STORE_FILE=keystore.jks
RELEASE_STORE_PASSWORD=***
RELEASE_KEY_ALIAS=***
RELEASE_KEY_PASSWORD=***
```

说明：

- `CLOUDBASE_URL` 未配置时，代码中有默认地址回退
- `FORCE_MOCK_HEALTH_DATA` 默认读取为 `true`，如果要接真实 OPPO 健康数据，需要显式关闭并完成授权
- API Key 和签名信息不会写入源码，而是通过 `BuildConfig` 注入

## 运行方式

### 运行 Android 主应用

1. 使用 Android Studio 打开仓库根目录 `E:\SilverLink`
2. 确认 `local.properties` 已配置
3. 同步 Gradle
4. 运行 `app` 模块

首次运行通常会请求以下权限：

- 通知
- 录音
- 相机
- 精确位置 / 后台位置
- 传感器 / 活动识别

### 运行 Wear OS 应用

1. 连接 Wear OS 模拟器或真机
2. 运行 `wear` 模块
3. 如需联调手机与手表逻辑，同时启动 `app` 与 `wear`

### 云函数部署

云函数位于 `cloud-functions/`，包含以下类别：

- 配对：`pairing-create`、`pairing-verify`、`pairing-get-elder`
- 用药：`medication-add`、`medication-update`、`medication-delete`、`medication-list`、`medication-log`、`medication-query`
- 情绪与认知：`mood-log`、`mood-query`、`cognitive-log`、`cognitive-report`
- 安全：`alert-send`、`alert-query`、`alert-dismiss`
- 记忆相册：`memory-photo-credentials`、`memory-photo-upload`、`memory-photo-save`、`memory-photo-list`、`memory-photo-search`
- 位置：`location-update`、`location-query`
- 声音复刻：`voice-audio-credentials`、`voice-audio-upload`、`voice-audio-get-url`、`voice-audio-download`

部署细节见 [cloud-functions/README.md](cloud-functions/README.md)。

## OPPO 健康集成说明

- 仓库已包含 OPPO Maven 仓库配置和桥接模块
- `settings.gradle.kts` 已声明 OPPO 相关仓库
- `oppo-sdk-bridge` 中封装了真实桥接和 Mock 桥接
- `SilverLinkApp` 会在用户授权且同意后尝试初始化 SDK
- 健康记录页已接入聚合数据读取逻辑

如果只是本地开发，建议先保持：

```properties
FORCE_MOCK_HEALTH_DATA=true
```

如果要切换真实数据：

1. 将 `FORCE_MOCK_HEALTH_DATA=false`
2. 安装 OPPO 健康 App
3. 在应用内完成合规确认与授权

补充文档见 [docs/oppo-health-sdk-integration.md](docs/oppo-health-sdk-integration.md)。

## 研发文档

- [功能总览](docs/features/summary.md)
- [聊天与陪伴](docs/features/chat_companion.md)
- [安全与跌倒检测](docs/features/safety_alert.md)
- [位置追踪](docs/features/location_tracking.md)
- [记忆时光机](docs/features/memory_time_machine.md)
- [健康与用药](docs/features/health_management.md)
- [设备配对](docs/features/device_pairing.md)
- [认知与情绪追踪](docs/features/cognitive_mood.md)
- [OPPO 健康 SDK 接入交接](docs/oppo-health-sdk-integration.md)

## 当前仓库特征

- 这是一个以真实业务代码为主的仓库，不只是 Demo 页面
- 仓库中同时保留了模型训练脚本、研究文档、实验数据和调试记录
- 部分手表连接逻辑当前仍使用 `MockNearbyBridge`
- 云函数、OPPO 健康、DashScope、COS 上传都依赖外部环境配置，离开配置后不会完整工作

## License

仓库当前未看到单独的 License 文件；如需开源发布，建议补充明确许可证文本后再对外分发。
