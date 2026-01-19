# 主动关怀服务 (Proactive Interaction)

## 概述

检测老人久坐不动，主动唤醒屏幕并播放 AI 生成的个性化问候语。如果连续多次唤醒无响应，向家人端发送警报。

## 实现文件

| 文件 | 作用 |
|------|------|
| `app/.../feature/proactive/ProactiveInteractionService.kt` | 前台服务，核心逻辑 |
| `app/.../data/remote/CloudBaseService.kt` | 发送警报到云端 |
| `app/.../data/repository/SyncRepository.kt` | 家人端获取警报 |
| `app/.../ui/family/FamilyMonitoringViewModel.kt` | 家人端警报轮询 |
| `app/.../ui/family/FamilyMonitoringScreen.kt` | 家人端警报横幅 UI |

## 核心逻辑

```
┌─────────────────────────────────────────────────────────────┐
│  每60秒检查一次                                              │
│  ↓                                                          │
│  计算 timeSinceLastMove = 当前时间 - lastMovementTime       │
│  ↓                                                          │
│  如果 > 阈值 (3小时/10秒debug) 且 光线足够                   │
│  ↓                                                          │
│  触发唤醒 → 播放AI问候语 → 失败计数+1                        │
│  ↓                                                          │
│  下次触发时检查是否有真实移动                                │
│  ├─ 有移动 → 重置失败计数 + 重置计时器                       │
│  └─ 无移动 → 保持失败计数                                    │
│  ↓                                                          │
│  连续失败 ≥ 2次 → 发送警报给家人                             │
└─────────────────────────────────────────────────────────────┘
```

## 关键类/函数

### ProactiveInteractionService

| 函数 | 作用 |
|------|------|
| `checkInactivity()` | 每分钟检查久坐状态 |
| `triggerProactiveInteraction()` | 触发唤醒、播放语音、更新计数 |
| `checkPreviousWakeUpResponse()` | 检查用户是否响应了上次唤醒 |
| `onSensorChanged()` | 处理计步器/加速度计/光线传感器数据 |
| `sendInactivityAlertToFamily()` | 发送警报到云端 |

### 移动检测

- **计步器**: `TYPE_STEP_COUNTER` - 检测走动
- **加速度计**: `TYPE_ACCELEROMETER` - 检测手机被拿起/移动（备用）
- **光线传感器**: `TYPE_LIGHT` - 判断环境是否明亮

## 配置参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `INACTIVITY_THRESHOLD_MS_DEBUG` | 10秒 | 调试用阈值 |
| `INACTIVITY_THRESHOLD_MS_REAL` | 3小时 | 生产环境阈值 |
| `CHECK_INTERVAL_MS` | 60秒 | 检查间隔 |
| `LIGHT_THRESHOLD_LUX` | 10 lux | 光线阈值 |
| `MAX_CONSECUTIVE_FAILURES` | 2次 | 连续失败触发警报 |
| `ACCEL_MOVEMENT_THRESHOLD` | 1.5 m/s² | 加速度变化阈值 |

## 测试方法

1. 切换 `INACTIVITY_THRESHOLD_MS` 到 `DEBUG` 模式（10秒）
2. 安装到长辈设备，配对家人端
3. 长辈端激活后，放置不动
4. 观察日志：10秒后应触发唤醒
5. 连续2次无响应后，家人端应显示警报横幅

## 修改记录

| 日期 | 修改内容 |
|------|----------|
| 2026-01-19 | 初始实现：久坐检测 + AI问候语 + 前台服务 |
| 2026-01-19 | 修复 `lastRealMovementTime` 未更新的 bug |
| 2026-01-19 | 添加加速度计作为备用移动检测 |
| 2026-01-19 | 修复用户响应后未重置计时器的 bug |
| 2026-01-19 | 添加家人端警报获取和显示功能 |
