# OPPO Watch 手表端开发进展

## 已完成工作

### Phase 1: 多模块架构搭建 (已完成)

#### 1. `shared` 模块 — 共享代码库
| 文件 | 说明 |
|------|------|
| `shared/detection/AccelerometerBuffer.kt` | 加速度滑动窗口缓冲区（从 app 模块提取，去除 Android 依赖） |
| `shared/detection/AccelFeatures.kt` | 加速度特征数据类（14 维特征向量） |
| `shared/detection/FallClassifier.kt` | 跌倒分类器（5 层特征评分 + 时序模式识别），用 Logger 接口替代 android.util.Log |
| `shared/model/MedicationInfo.kt` | 药物信息模型（id、名称、剂量、时间列表） |
| `shared/model/HealthData.kt` | 健康数据模型（心率、步数、睡眠、血氧） |
| `shared/model/EmergencyContact.kt` | 紧急联系人模型 |
| `shared/model/FallAlert.kt` | 跌倒警报模型（时间戳、严重性、置信度） |
| `shared/protocol/WatchMessage.kt` | 通信协议 8 种消息类型（sealed class + kotlinx.serialization） |
| `shared/protocol/MessageSerializer.kt` | JSON 序列化/反序列化工具 |

#### 2. `oppo-sdk-bridge` 模块 — SDK 抽象层
| 文件 | 说明 |
|------|------|
| `sdk/health/HealthServiceBridge.kt` | 健康服务接口（心率、步数、睡眠、血氧） |
| `sdk/health/MockHealthServiceBridge.kt` | Mock 实现，返回随机模拟数据 |
| `sdk/health/SleepData.kt` | 睡眠数据模型 |
| `sdk/nearby/NearbyBridge.kt` | 近距离通讯接口 + DeviceDiscoveryCallback / ConnectionCallback |
| `sdk/nearby/MockNearbyBridge.kt` | Mock 实现，模拟设备发现和消息传递 |
| `sdk/nearby/DeviceInfo.kt` | 设备信息模型 |

#### 3. `wear` 模块 — 手表端 App
**UI 界面（7 个屏幕，适配圆形屏幕）：**
| 屏幕 | 文件 | 功能 |
|------|------|------|
| 主页 | `WatchNavigation.kt` | 心率数字 + 4 个快捷入口（健康/SOS/用药/睡眠） |
| 健康面板 | `HealthDashboard.kt` | 心率、步数、血氧实时展示 |
| SOS 紧急求助 | `SOSScreen.kt` | 脉冲动画大按钮，点击触发呼叫 |
| 药物提醒 | `MedicationScreen.kt` | 今日用药列表 + 确认服药 |
| 睡眠报告 | `SleepReportScreen.kt` | 睡眠时长、各阶段分布（深睡/浅睡/REM） |
| 心脏健康 | `HeartHealthScreen.kt` | 当前心率、最高/最低、状态判断 |
| 设置 | `WatchSettingsScreen.kt` | 跌倒检测开关、灵敏度调节 |

**后台服务：**
| 服务 | 文件 | 功能 |
|------|------|------|
| 跌倒检测 | `WatchFallDetectionService.kt` | 复用 shared 算法，前台服务 + WakeLock 常驻运行 |
| 健康监测 | `WatchHealthMonitorService.kt` | 定期采集健康数据（预留 SDK 接入点） |
| 药物闹钟 | `MedicationAlarmReceiver.kt` | 接收闹钟广播，启动提醒界面 |
| 开机启动 | `BootReceiver.kt` | 开机自动启动跌倒检测 |

**警报界面：**
| Activity | 文件 | 功能 |
|----------|------|------|
| 跌倒警报 | `FallAlertActivity.kt` | 30 秒倒计时 + 脉冲动画，超时自动呼救 |
| 药物提醒 | `MedicationAlertActivity.kt` | 药物名称/剂量展示，已服药/稍后提醒按钮 |

#### 4. 构建配置修改
- `settings.gradle.kts` — 注册 `:shared`、`:wear`、`:oppo-sdk-bridge` 模块
- `build.gradle.kts`（根） — 添加 `android-library` 插件
- `gradle/libs.versions.toml` — 添加 VAD、serialization 版本和 android-library 插件
- `app/build.gradle.kts` — 添加 `shared` 和 `oppo-sdk-bridge` 依赖

---

## 剩余工作

### Phase 2: 手表端功能完善（无需 SDK）
- [ ] 将手表端 UI 中的硬编码数据替换为 WatchPreferences（SharedPreferences 封装）
- [ ] 手表端药物列表从手机端同步逻辑
- [ ] 手表端紧急联系人从手机端同步逻辑
- [ ] SOS 界面接入实际拨号/通知功能
- [ ] 跌倒警报倒计时结束后通过 NearbyBridge 通知手机端

### Phase 3: 通信层（无需 SDK，Mock 通信）
- [ ] 手机端 `WatchConnectionService` — 前台服务维护与手表的连接
- [ ] 手机端 `WatchSyncManager` — 药物/联系人/健康数据同步管理
- [ ] 手机端 `WatchManagementScreen` — 手表配对、连接状态 UI
- [ ] 手机端 `WatchHealthScreen` — 展示手表同步的健康数据
- [ ] 使用 MockNearbyBridge 进行端到端调试

### Phase 4: OPPO SDK 集成（需要 SDK 文件）
- [ ] 集成 OPPO Health SDK，实现 `HealthServiceBridge` 真实版本
- [ ] 集成 OPPO Nearby Communication SDK，实现 `NearbyBridge` 真实版本
- [ ] 接入真实健康数据（心率、步数、睡眠、血氧）
- [ ] 真实设备间蓝牙通讯测试

### Phase 5: 健康功能完善（需要 SDK）
- [ ] 心率实时监测和异常告警（过高/过低阈值）
- [ ] 睡眠质量数据采集和报告生成
- [ ] 健康数据上传至 CloudBase（与现有云端同步整合）
- [ ] 手机端家属监控页面展示手表健康数据

### 其它待办
- [ ] app 模块中的 `FallDetectionService` 改为引用 shared 模块的算法（目前两份代码共存）
- [ ] 手表端 launcher icon 替换为正式设计图
- [ ] 手表端 UI 在 OPPO Watch 真机上适配调试
- [ ] `feat/realtime-echo-cancellation` 分支上的手表 commit 需要清理（该 commit 应仅存在于 `feat/oppo-watch-support` 分支）
