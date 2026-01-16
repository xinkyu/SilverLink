# 🌟 SilverLink 银链

> 专为老年人设计的 AI 陪伴助手 Android 应用

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose"/>
</p>

## ✨ 功能特色

### 🤖 AI 对话陪伴
- **按住说话** - 长按录音，松开自动识别并发送
- 温暖的 AI 助手"小银"，专门陪伴老人聊天
- 自动检测诈骗信息并预警
- 身体不适时提醒联系家人

### 💊 智能吃药提醒
- **AI 拍照识别** - 拍摄药品包装，自动识别药名、剂量、服药时间
- 支持多个服药时间点（如早中晚各一次）
- 全屏提醒 + 响铃，确保不会错过
- 每日自动重置服用状态

### 📅 日程提醒（开发中）
- 重要日期提醒
- 日历视图

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| **Kotlin** | 100% Kotlin 开发 |
| **Jetpack Compose** | 现代声明式 UI |
| **Material 3** | 适合老年人的大字体、高对比度设计 |
| **Room** | 本地数据持久化 |
| **Retrofit** | 网络请求 |
| **WorkManager** | 每日重置任务（凌晨清零服用状态） |
| **Alibaba DashScope** | AI 大模型服务 |

## 🔌 AI 能力

- **Qwen 对话** - 阿里云通义千问大模型
- **Qwen2-Audio** - 语音识别（按住说话录音 → ASR）
- **Qwen-VL** - 视觉识别（拍照识别药名/剂量/时间）

## 📱 截图



## 🚀 快速开始

### 前置条件

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34

### 配置 API Key

请在 [app/src/main/java/com/silverlink/app/data/remote/RetrofitClient.kt](app/src/main/java/com/silverlink/app/data/remote/RetrofitClient.kt) 中把 `API_KEY` 替换成你自己的阿里云 DashScope Key。

### 使用 Android Studio 运行（推荐）

1. 打开 Android Studio → **Open** → 选择项目根目录 `SilverLink`
2. 等待 Gradle Sync 完成（首次可能较慢）
3. 连接真机或启动模拟器
4. 右上角选择运行配置 **app** → 点击 **Run**

首次运行时：
- Android 13+ 会请求通知权限（用药提醒需要）
- 语音输入会请求录音权限
- 拍照识别会请求相机权限

### 命令行构建（可选）

```bash
git clone https://github.com/xinkyu/SilverLink.git
cd SilverLink
./gradlew assembleDebug
```

Windows 下也可以使用：

```bash
gradlew.bat assembleDebug
```

## 📂 项目结构

```
app/src/main/java/com/silverlink/app/
├── data/
│   ├── local/          # Room 数据库
│   └── remote/         # Retrofit API
├── feature/
│   ├── chat/           # 录音和语音识别
│   └── reminder/       # 吃药提醒逻辑
└── ui/
    ├── chat/           # 聊天界面
    ├── reminder/       # 提醒界面
    └── theme/          # 主题配置
```

## 📄 License

MIT License

---

<p align="center">
  用爱连接，让科技温暖每一位老人 ❤️
</p>
