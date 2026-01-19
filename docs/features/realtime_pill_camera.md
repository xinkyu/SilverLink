# 实时相机服药确认

## 概述

将原本的"拍照-确认-识别"流程改为类似 AI 视频对话的实时相机预览模式。用户只需对准药瓶稳住画面，系统自动截图并识别，无需手动按快门。

## 实现文件

- `app/src/main/java/com/silverlink/app/ui/reminder/PillCheckCameraScreen.kt` - 实时相机预览界面
- `app/src/main/java/com/silverlink/app/ui/reminder/MotionDetector.kt` - 运动检测器
- `app/build.gradle.kts` - CameraX 依赖配置

## 核心逻辑

### 1. 实时预览

使用 CameraX 的 `Preview` 用例实现全屏相机预览，配合 `ImageAnalysis` 用例进行逐帧分析。

### 2. 运动检测

`MotionDetector` 通过比较前后两帧的像素差异判断画面是否稳定：

- 对图片进行降采样（32x32）以提高性能
- 计算相邻帧的像素差异百分比
- 差异 < 1.5% 判定为稳定帧

### 3. 防抖与自动截图

- 画面稳定后开始计时
- 稳定超过 500ms 自动触发截图
- 画面晃动时重置计时器，防止误触发

### 4. 静默上传

截图后自动调用识别接口，用户无需额外操作。

## 关键类/函数

### MotionDetector

| 函数                    | 作用                     |
| ----------------------- | ------------------------ |
| `analyzeFrame(bitmap)`  | 分析当前帧，返回是否稳定 |
| `calculateDifference()` | 计算两帧像素差异百分比   |
| `reset()`               | 重置检测器状态           |

### PillCheckCameraScreen

| 组件                        | 作用                      |
| --------------------------- | ------------------------- |
| `CameraPreviewWithAnalysis` | CameraX 预览 + 帧分析组合 |
| `StabilityIndicator`        | 稳定进度条 UI             |
| `AutoCaptureOverlay`        | 截图倒计时遮罩            |

## 配置参数

| 参数                   | 默认值 | 说明                   |
| ---------------------- | ------ | ---------------------- |
| `STABILITY_THRESHOLD`  | 500ms  | 稳定多久后自动截图     |
| `DIFFERENCE_THRESHOLD` | 1.5%   | 判定稳定的像素差异阈值 |
| `SAMPLE_SIZE`          | 32x32  | 帧对比的降采样尺寸     |

## 集成位置

1. **用药提醒弹窗** - `ReminderAlertActivity.kt`
   - 点击"帮我服药"按钮触发

2. **聊天界面** - `ChatScreen.kt`
   - 聊天页面点击摄像头

## 测试方法

1. 触发用药提醒或在聊天页面点击摄像头
2. 相机预览界面应全屏显示
3. 对准药瓶晃动时，进度条不应前进
4. 稳住画面 0.5 秒后应自动截图
5. 截图后自动跳转到识别结果

## 依赖

```kotlin
// CameraX
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
```

## 修改记录

| 日期       | 修改内容                                      |
| ---------- | --------------------------------------------- |
| 2026-01-19 | 初始实现：CameraX 实时预览 + 运动检测自动截图 |
