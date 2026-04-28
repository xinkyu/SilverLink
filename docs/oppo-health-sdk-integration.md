# OPPO健康服务SDK接入交接文档（给后续AI）

本文档用于让后续 AI 在不了解上下文的情况下，快速接着做 OPPO 健康 SDK 工作。

## 1. 本轮已完成内容（事实清单）

1. 构建和依赖

- 在 `settings.gradle.kts` 增加 OPPO Maven 仓库（releases/snapshots，含账号密码）。
- 在 `oppo-sdk-bridge/build.gradle.kts` 增加依赖 `com.heytap.health:sdk:2.1.7`。

2. 桥接层（oppo-sdk-bridge）

- 扩展健康桥接接口 `HealthServiceBridge`：初始化、授权、撤销授权、查询 scope、安装检测、下载引导、分类读取能力。
- 新增工厂 `HealthServiceBridgeFactory`：优先真实桥接，不可用自动回退 Mock。
- 新增真实实现 `OppoHealthServiceBridge`：
  - 使用反射调用 Heytap SDK（降低包名/版本耦合）。
  - 封装 `dataApi.read(DataReadRequest, HResponse)`。
  - 解析 `List<DataSet> -> DataPoint -> Element`。
  - onFailure 错误码映射为 `HealthSdkException(code)`。
- 新增模型：
  - `DailyActivitySummary`
  - `HealthValuePoint`
  - `SleepStagePoint`
  - `HealthSdkException`
- 更新 `MockHealthServiceBridge` 以支持新接口，便于无真机/无健康App环境联调。

3. App 管理层（app/feature/health）

- 新增 `OppoHealthSdkManager`：
  - `initializeIfConsented`
  - `requestAuthorization`
  - `revokeAuthorization`
  - `getAuthorizedScopes`
  - `pullDashboardData`（按分类聚合）
  - `getErrorCode`
- 新增 UI 聚合模型 `OppoHealthDashboardData` 和 `HealthTrendPoint`。
- 处理错误码 `100007`（健康App未安装）：触发 `openHealthAppDownload(activity)`。

4. 合规同意与应用接线

- 在 `UserPreferences` 增加：
  - `isOppoHealthSdkConsentGranted()`
  - `setOppoHealthSdkConsentGranted(Boolean)`
- 在 `SilverLinkApp` 中仅在已同意时初始化 SDK。
- `AndroidManifest.xml` 增加 Android 11+ queries：
  - `com.heytap.health`
  - `com.heytap.market`
  - `com.oppo.market`

5. 健康记录页 UI 重写

- 位置：`app/src/main/java/com/silverlink/app/ui/history/HistoryScreen.kt`
- 已改成：
  - 顶栏：左侧“健康记录”，右侧刷新按钮。
  - 中间：今日健康摘要大卡。
  - 下方：双列小矩形卡片网格（每类一个小卡片）。
- 页面保留合规弹窗和授权入口。

## 2. 当前数据接入状态（已接/待接）

### 2.1 已接入可展示真实值

- `TYPE_DAILY_ACTIVITY_COUNT`（步数、热量、距离、活动分钟）
- `TYPE_HEART_RATE`（心率时间序列）
- `TYPE_HEART_RATE_COUNT`（通过当前心率/统计值映射展示）
- `TYPE_BLOOD_OXYGEN_COUNT`（血氧统计）
- `TYPE_SLEEP`（睡眠详情时间段已可解析）
- `TYPE_SLEEP_COUNT`（睡眠总时长/分项统计）

### 2.2 UI 已预留卡片、数据仍为占位

- `TYPE_PRESSURE`
- `TYPE_PRESSURE_COUNT`
- `TYPE_ECG`
- `TYPE_HEARING_HEALTH`
- `TYPE_HEARING_HEALTH_COUNT`
- `TYPE_RELAX`
- `TYPE_RELAX_COUNT`
- `TYPE_BLOOD_PRESSURE`
- `TYPE_BLOOD_PRESSURE_COUNT`
- `TYPE_BODY_WEIGHT`
- `TYPE_SPORT_METADATA`
- `TYPE_GYM_STRENGTH_TRAINING`
- `TYPE_TRAINING_ACTION`

## 3. 关键文件索引（后续AI优先看）

### 3.1 构建配置

- `settings.gradle.kts`
- `oppo-sdk-bridge/build.gradle.kts`

### 3.2 桥接层

- `oppo-sdk-bridge/src/main/java/com/silverlink/sdk/health/HealthServiceBridge.kt`
- `oppo-sdk-bridge/src/main/java/com/silverlink/sdk/health/HealthServiceBridgeFactory.kt`
- `oppo-sdk-bridge/src/main/java/com/silverlink/sdk/health/OppoHealthServiceBridge.kt`
- `oppo-sdk-bridge/src/main/java/com/silverlink/sdk/health/MockHealthServiceBridge.kt`
- `oppo-sdk-bridge/src/main/java/com/silverlink/sdk/health/HealthMetricsModels.kt`

### 3.3 App 层

- `app/src/main/java/com/silverlink/app/feature/health/OppoHealthSdkManager.kt`
- `app/src/main/java/com/silverlink/app/feature/health/HealthUiModels.kt`
- `app/src/main/java/com/silverlink/app/data/local/UserPreferences.kt`
- `app/src/main/java/com/silverlink/app/SilverLinkApp.kt`
- `app/src/main/java/com/silverlink/app/ui/history/HistoryViewModel.kt`
- `app/src/main/java/com/silverlink/app/ui/history/HistoryScreen.kt`
- `app/src/main/java/com/silverlink/app/ui/history/HistoryHealthSection.kt`（旧健康卡片，可视情况合并/删除）

## 4. 当前页面行为说明（避免误判）

1. 进入健康记录页时

- 若未同意 SDK 合规，弹隐私说明对话框。
- 同意后写入本地偏好并触发健康数据加载。

2. 点击“绑定OPPO健康”

- 调用 `OppoHealthSdkManager.requestAuthorization(activity)`。
- 会先检查健康App安装；未安装则引导下载。

3. 刷新按钮

- 顶栏刷新会触发 `viewModel.refresh()`，包含健康数据刷新。

## 5. 数据流（从 SDK 到 UI）

1. UI -> ViewModel

- `HistoryScreen` 触发加载/授权。

2. ViewModel -> Manager

- `HistoryViewModel.loadHealthData()`
- 调 `OppoHealthSdkManager.pullDashboardData(context, date)`

3. Manager -> Bridge

- 调 `HealthServiceBridgeFactory.get(...).xxx()`

4. Bridge -> SDK

- `OppoHealthServiceBridge` 构建 `DataReadRequest`。
- `dataApi.read(...)` 拿到 `List<DataSet>`。

5. Bridge 解析返回

- `DataSet -> DataPoint -> Element` 映射到 `DailyActivitySummary/HealthValuePoint/SleepStagePoint`。

6. Manager 聚合

- 组装为 `OppoHealthDashboardData` 给 UI 渲染。

## 6. 错误处理规范

1. SDK 错误码

- 回调失败统一转 `HealthSdkException(code)`。

2. 未安装健康App（100007）

- 在授权前做安装检查。
- 授权失败码=100007时再次兜底调用下载引导。

3. UI 文案

- `HistoryViewModel` 将错误码映射成人类可读提示。

<!-- AI辅助生成：工具版本待补充，生成时间待补充（于 2026-04-16 复核标注） -->

## 7. 给后续AI的推荐续做任务（按优先级）

1. 把占位卡片改为真实数据

- 先接：压力、血压、体重。
- 再接：ECG、听力、放松、运动概要。

2. 把摘要卡做成“今日全部指标统计”

- 当前是核心字段摘要；可补总指标计数、异常计数、趋势箭头。

3. 清理 UI 代码重复

- `HistoryHealthSection.kt` 与 `HistoryScreen.kt` 有能力重叠，建议收敛到一套组件。

4. 回归测试建议

- 无健康App
- 有健康App未授权
- 授权后有数据
- 授权后无数据
- 撤销授权后重新授权

## 8. 已验证状态

- 命令：`./gradlew :app:compileDebugKotlin`
- 结果：`BUILD SUCCESSFUL`
- 当前存在的是项目历史 warning，不是本次阻断错误。

## 9. 合规文案模板（可直接用于产品文案）

- 第三方 SDK 名称：OPPO健康服务SDK
- 第三方公司名称：广东欢太科技有限公司
- 收集个人信息种类：应用基本信息（健康APP包名、应用商店包名）
- 使用目的：用于判断应用安装状态，以支持APP端与第三方数据传输
- 隐私政策链接：
  `https://sport.health.heytapmobi.com/h5/statement/index.html#/?langCode=zh-CN&appPackName=com.heytap.health:sdk&contentType=16&osType=1&appVersion=`
