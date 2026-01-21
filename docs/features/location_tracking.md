# 位置追踪 (Location Tracking)

为家属提供长辈的实时位置信息，确保出行安全。

## 功能特性

### 1. 实时位置共享
- **定期更新**：设备每 5 分钟自动获取一次高精度位置（GPS + 网络定位）。
- **后台保活**：通过前台服务 (`LocationTrackingService`) 确保应用在后台或锁屏状态下仍能持续上传位置。
- **智能策略**：使用 "Balanced Power Accuracy" 策略，平衡定位精度与电量消耗。

### 2. 反地理编码
- **自动解析**：将经纬度坐标自动转换为可读的详细中文地址（省/市/区/街道）。
- **本地优先**：优先使用 Android 系统原生 `Geocoder` 进行解析，无需消耗额外的 API 额度（*注：这部分逻辑在代码中已实现，但Android系统服务可能因网络原因不稳定*）。

### 3. 数据隐私
- **短期存储**：云端数据库仅保留最近 **2小时** 内的轨迹记录，过期数据自动清理，最大程度保护长辈隐私。
- **权限安全**：严格遵循 Android 隐私规范，申请 `ACCESS_FINE_LOCATION` 和 `ACCESS_BACKGROUND_LOCATION` 权限，并提供显式的前台服务通知。

## 技术实现

- **LocationTrackingService**:
    - 使用 Google Play Services 的 `FusedLocationProviderClient` 获取位置。
    - 维护一个前台通知 (`NOTIFICATION_ID = 3001`) 告知用户正在共享位置。
- **Cloud Function (`location-update`)**:
    - 接收客户端上传的经纬度、精度和地址信息。
    - 执行自动清理逻辑，删除该设备 2 小时前的旧数据。
- **LocationHelper**: 辅助工具类，用于计算距离或格式化地址。

## 注意事项
- 首次使用需授予"始终允许"的位置权限以支持后台定位。
- 室内环境下可能会因 GPS 信号弱导致定位漂移。
