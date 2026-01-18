# 🌟 SilverLink（银发连接）

面向老年人与家属的双端 AI 陪伴与辅助应用：**老人端**更易用，**家人端**可远程看护与同步信息。项目以 Android 为主端，后端使用云函数承载轻量业务。

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose"/>
</p>

## 🎯 核心功能

- **AI 陪伴对话**：按住说话 → 语音识别 → 智能回复
- **AI 视觉识别**：拍照识别药品名称、剂量与服药时间
- **智能用药提醒**：多时段提醒、全屏提示、每日重置
- **家属远程同步**：配对码绑定老人端与家人端
- **情绪与用药记录**：云端记录与家属端查看

## 🧩 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 客户端 | Kotlin / Jetpack Compose / Material 3 | 大字体与高对比度界面、现代化 UI |
| 数据 | Room | 本地持久化（用药/记录） |
| 网络 | Retrofit + OkHttp | REST 调用云函数与 AI 服务 |
| 任务 | WorkManager | 每日重置与后台任务 |
| 序列化 | kotlinx-serialization | JSON 编解码 |
| 工具 | ZXing | 配对二维码生成 |
| AI | Alibaba DashScope | Qwen 对话 / Qwen2-Audio / Qwen-VL |
| 后端 | 腾讯云 CloudBase | Node.js 云函数 + 云数据库 |

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
├── pairing-create/     # 创建配对码
├── pairing-verify/     # 验证配对码
├── pairing-get-elder/  # 获取已配对老人
├── medication-log/     # 记录服药
├── medication-query/   # 查询服药记录
├── mood-log/           # 记录情绪
└── mood-query/         # 查询情绪记录
```

## 🚀 快速开始

### 前置条件

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34

### 1) 配置 AI Key

在 [app/src/main/java/com/silverlink/app/data/remote/RetrofitClient.kt](app/src/main/java/com/silverlink/app/data/remote/RetrofitClient.kt) 中替换 `API_KEY` 为你的阿里云 DashScope Key。

### 2) 配置 CloudBase 访问地址

在 [app/src/main/java/com/silverlink/app/data/remote/CloudBaseService.kt](app/src/main/java/com/silverlink/app/data/remote/CloudBaseService.kt) 中替换 `CLOUD_BASE_URL` 为你的云函数 HTTP 访问地址。

### 3) 运行 Android 客户端

1. 打开 Android Studio → **Open** → 选择项目根目录 SilverLink
2. 等待 Gradle Sync 完成（首次可能较慢）
3. 连接真机或启动模拟器
4. 选择运行配置 **app** → **Run**

首次运行时：
- Android 13+ 会请求通知权限（用药提醒）
- 语音输入会请求录音权限
- 拍照识别会请求相机权限

### 4) 部署云函数（可选，但建议）

参考 [cloud-functions/README.md](cloud-functions/README.md) 完成 CloudBase 环境创建、数据库集合与云函数部署。

## 🔐 安全与隐私

- 建议将 API Key 与 CloudBase 地址放入安全配置（如 `local.properties` 或 CI Secret），避免硬编码提交
- 上传云端的用药/情绪日志建议进行脱敏与最小化字段存储

## 📄 License

MIT License

---

<p align="center">用爱连接，让科技温暖每一位老人 ❤️</p>
