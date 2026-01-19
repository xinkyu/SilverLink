# 多模态记忆回溯功能 - 开发交接文档

> 📅 更新时间: 2026-01-19
> 🎯 功能目标: 实现 Memory Time-Machine (记忆时光机)

---

## 1. 功能概述

基于 **Qwen-VL + CloudBase** 的多模态记忆回溯功能，包括：

- **Photo Talk (老照片的故事)**: 家人上传老照片，老人可以浏览并与 AI 问答
- **Digital Amnesia Defense (数字遗忘胶囊)**: 定期展示照片测试老人认知能力

### 技术方案选择

| 对比项   | 原设计 (CLIP + ObjectBox) | ✅ 已采用 (Qwen-VL + CloudBase) |
| -------- | ------------------------- | ------------------------------- |
| 向量生成 | 本地 CLIP 模型            | Qwen-VL 云端生成文字描述        |
| 存储     | ObjectBox 向量数据库      | CloudBase 云数据库              |
| 优势     | 完全离线                  | 复用现有架构，开发成本低        |

---

## 2. 已完成工作 ✅

### 2.1 数据层 (100%)

| 文件                                      | 描述                   | 状态 |
| ----------------------------------------- | ---------------------- | ---- |
| `data/local/entity/MemoryPhotoEntity.kt`  | 记忆照片本地缓存实体   | ✅   |
| `data/local/entity/CognitiveLogEntity.kt` | 认知评估记录实体       | ✅   |
| `data/local/dao/MemoryPhotoDao.kt`        | 照片 CRUD + 搜索 DAO   | ✅   |
| `data/local/dao/CognitiveLogDao.kt`       | 认知记录 DAO           | ✅   |
| `data/local/AppDatabase.kt`               | 数据库版本升级到 v5    | ✅   |
| `data/remote/CloudBaseApi.kt`             | 添加照片/认知 API 接口 | ✅   |
| `data/remote/CloudBaseService.kt`         | 添加业务层封装方法     | ✅   |

### 2.2 云函数 (100%)

| 函数目录                               | 功能              | 状态 |
| -------------------------------------- | ----------------- | ---- |
| `cloud-functions/memory-photo-upload/` | 上传照片 + 元数据 | ✅   |
| `cloud-functions/memory-photo-list/`   | 分页获取照片列表  | ✅   |
| `cloud-functions/memory-photo-search/` | 关键词搜索照片    | ✅   |
| `cloud-functions/cognitive-log/`       | 记录认知测试结果  | ✅   |
| `cloud-functions/cognitive-report/`    | 生成认知评估报告  | ✅   |

### 2.3 工具类 (100%)

| 文件                                     | 描述                       | 状态 |
| ---------------------------------------- | -------------------------- | ---- |
| `feature/memory/ImageUtils.kt`           | 图片压缩/Base64编码        | ✅   |
| `feature/memory/PhotoAnalysisService.kt` | Qwen-VL 照片分析           | ✅   |
| `feature/memory/CognitiveQuizService.kt` | 认知评估问题生成与答案验证 | ✅   |

### 2.4 UI 界面 (100%)

| 文件                                  | 描述               | 状态 |
| ------------------------------------- | ------------------ | ---- |
| `ui/memory/MemoryLibraryScreen.kt`    | 家人端照片库主屏   | ✅   |
| `ui/memory/MemoryLibraryViewModel.kt` | 家人端 ViewModel   | ✅   |
| `ui/memory/PhotoUploadDialog.kt`      | 照片上传对话框     | ✅   |
| `ui/memory/MemoryGalleryScreen.kt`    | 老人端沉浸式画廊   | ✅   |
| `ui/memory/MemoryGalleryViewModel.kt` | 老人端 ViewModel   | ✅   |
| `ui/memory/MemoryQuizScreen.kt`       | 认知测验 UI        | ✅   |
| `ui/memory/MemoryQuizViewModel.kt`    | 认知测验 ViewModel | ✅   |
| `ui/MainScreen.kt`                    | 导航集成 (4+2 Tab) | ✅   |
| `ui/chat/ChatScreen.kt`               | 语音意图导航集成   | ✅   |

### 2.5 依赖添加 (100%)

```kotlin
// app/build.gradle.kts 已添加
implementation("io.coil-kt:coil-compose:2.5.0")  // 图片加载
```

---

## 3. 待完成工作 ⏳

### ~~3.1 编译修复~~ ✅ 已完成

已修复 `ChatScreen.kt` 中 `onNavigateToGallery` 参数缺失问题。

### ~~3.2 认知评估 UI~~ ✅ 已完成

需要创建以下文件：

```kotlin
已实现：照片展示、语音问答、AI 验证与结果动效。

### ~~3.3 认知衰退指数记录与周报生成~~ ✅ 已完成

- 本地保存认知测验记录与统计摘要
- 云端同步认知记录，支持报告生成

### ~~3.4 集成到 ProactiveInteractionService~~ ✅ 已完成

- 久坐唤醒后适度提醒进行记忆小游戏（每日最多一次）
```

### ~~3.3 语音集成~~ ✅ 已完成

已在 `ChatViewModel.kt` 实现 `detectPhotoIntent()` 方法，并在 `ChatScreen.kt` 中添加 `photoIntent` 状态监听，触发导航。

支持的语音关键词：

- 打开画廊："看照片"、"看看照片"、"老照片"、"翻相册"、"记忆相册"、"看相册"
- 搜索照片："找照片"、"搜照片"、"有没有"、"那年"、"那次"

### ~~3.5 后台同步~~ ✅ 已完成

- 新增 `feature/memory/MemorySyncService.kt`
- WorkManager 定时任务（12 小时）
- 仅 WiFi + 充电时同步
- 增量拉取新照片并缓存到本地

### 3.6 云函数部署（需手动执行）

需要在腾讯云 CloudBase 控制台完成：

1. 创建集合：`memory_photos`、`cognitive_logs`
2. 部署云函数：
   - `memory-photo-upload`
   - `memory-photo-list`
   - `memory-photo-search`
   - `cognitive-log`
   - `cognitive-report`
3. 配置 HTTP 访问路由并记录访问地址
4. 在 `local.properties` 中配置 `CLOUDBASE_URL`

---

## 4. 关键代码位置

### 数据流

```
家人上传照片:
FamilyMonitoringScreen → MemoryLibraryScreen → PhotoUploadDialog
    → MemoryLibraryViewModel.uploadPhoto()
    → PhotoAnalysisService.analyzePhoto() [AI 生成描述]
    → CloudBaseService.uploadMemoryPhoto()
    → cloud-functions/memory-photo-upload

老人浏览照片:
MainScreen (记忆相册 Tab) → MemoryGalleryScreen
    → MemoryGalleryViewModel.loadPhotos()
    → CloudBaseService.getMemoryPhotos()
```

### 关键类职责

| 类名                   | 职责                      |
| ---------------------- | ------------------------- |
| `PhotoAnalysisService` | 调用 Qwen-VL 分析图片内容 |
| `ImageUtils`           | 图片压缩、Base64 编码     |
| `MemoryPhotoDao`       | 本地照片缓存 CRUD         |
| `CloudBaseService`     | 云端 API 调用封装         |

---

## 5. 测试清单

### 功能测试

- [ ] 家人端选择照片上传成功
- [ ] AI 自动生成照片描述
- [ ] 老人端加载照片列表
- [ ] 左右滑动切换照片
- [ ] 照片搜索功能

### 集成测试

- [ ] 云函数正确响应
- [ ] 数据库版本迁移正常
- [ ] 网络错误处理

---

## 6. 开发指南

### 本地开发

```bash
# 编译检查
.\gradlew.bat compileDebugKotlin

# 构建 APK
.\gradlew.bat assembleDebug

# 安装到设备
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 云函数部署

```bash
cd cloud-functions/memory-photo-upload
npm install
tcb fn deploy memory-photo-upload
```

### 配置项

确保 `local.properties` 包含：

```properties
QWEN_API_KEY=your_api_key
CLOUDBASE_URL=https://your-env.service.tcloudbase.com/
```

---

## 7. 注意事项

1. **图片上传**: 云函数将 Base64 图片上传到 CloudBase 云存储并写入 `imageUrl/fileId`，本地仅保留元数据
2. **隐私保护**: 照片存储在云端，家人需先配对才能上传
3. **离线模式**: 当前版本不支持离线，需联网使用
4. **数据库迁移**: AppDatabase 版本已升至 5，使用 `fallbackToDestructiveMigration()`

---

## 8. 相关文档

- [实现计划](./implementation_plan.md) - 详细技术方案
- [云函数部署](../../cloud-functions/README.md) - CloudBase 部署指南
- [AI 开发规范](../../docs/ai_rules.md) - 功能文档规范
