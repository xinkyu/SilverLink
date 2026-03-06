# 端侧情绪识别模型调试与修复记录

## 概述

SilverLink App 集成了端侧 ONNX 情绪识别模型，用于实时检测用户文字和语音输入中的情绪状态。本文档记录了模型未能正常工作的问题排查与修复过程。

---

## 修复前的情况

### 现象

- 用户发送任何文字或语音输入后，情绪状态始终显示为 **NEUTRAL（平静）**
- Logcat 中 `EmotionRecognitionSvc` 和 `TranslationService` 无任何输出
- 情绪 Badge 始终不变化

### 关键日志

```
ChatViewModel  D  Saving mood log: emotion=NEUTRAL, date=2026-03-06, text=你好吗
ChatViewModel  E  ONNX text emotion analysis failed, using fallback
                  java.lang.IllegalStateException: EmotionRecognitionService not initialized

EmotionRecognitionSvc  E  Failed to initialize emotion recognition models
                          ai.onnxruntime.OrtException: Unsupported model IR version: 10, max supported IR version: 9

SpeechRecognitionService  E  ONNX speech emotion analysis failed, using text-based guess
                             java.lang.IllegalStateException: Speech emotion model not available
```

### 根因分析

| # | 问题 | 描述 |
|---|------|------|
| 1 | **ONNX Runtime 版本过低** | 项目使用 `onnxruntime-android 1.17.0`，仅支持 ONNX IR version ≤ 9，但文本模型 (`emotionEnglishDistilrobertaBase.onnx`) 使用 IR version 10 导出 |
| 2 | **语音模型量化算子不兼容** | 语音模型 (`emotion_model_mobile.onnx`) 使用 `ConvInteger` 量化算子，ONNX Runtime Android 不支持该算子 |
| 3 | **初始化耦合** | 文本模型和语音模型在同一个 `try-catch` 中顺序初始化，语音模型失败导致文本模型也无法使用 |
| 4 | **竞态条件** | `detectEmotionFromText()` 启动独立协程异步执行，但 `saveMessageToDb()` 在另一个协程中立即读取 `_currentEmotion.value`，此时检测还未完成 |
| 5 | **初始化无重试机制** | App 启动时初始化失败后，后续调用直接抛异常，无自动重试逻辑 |
| 6 | **模型缓存未更新** | 模型文件从 assets 拷贝到内部存储后，更新 APK 不会触发重新拷贝（仅检查文件是否存在） |

---

## 修复后的情况

### 修改清单

#### 1. 升级 ONNX Runtime（核心修复）

**文件**: `gradle/libs.versions.toml`

```diff
- onnxruntime = "1.17.0"
+ onnxruntime = "1.19.2"
```

ONNX Runtime 1.19.2 支持 IR version 10，解决文本模型加载失败的问题。

#### 2. 重新导出语音模型

**文件**: `app/src/main/assets/models/emotion_model_mobile.onnx`

- 基于 `ntu-spml/distilhubert` 主干 + `superb/hubert-base-superb-er` 分类器头
- 导出为 **float32 ONNX**（无 `ConvInteger` 算子）
- 标签顺序：`[neutral, happiness, anger, sadness]`
- 大小：92.3 MB（原量化版 22.8 MB）

#### 3. 拆分模型初始化

**文件**: `EmotionRecognitionService.kt`

- 文本模型初始化失败 → 抛异常（核心功能）
- 语音模型初始化失败 → 仅打印警告，不影响文本模型
- 新增 `isReady()` 和 `isSpeechModelReady` 标志

#### 4. 修复竞态条件

**文件**: `ChatViewModel.kt`

- `detectEmotionFromText()` 从 `fun`（启动独立协程）改为 `suspend fun`
- 在 `sendMessage()` 中顺序执行：先完成情绪检测，再保存消息和构建 prompt
- 新增自动重新初始化逻辑：如果服务未初始化，尝试 re-init

#### 5. 更新标签映射

**文件**: `SpeechEmotionClassifier.kt` / `EmotionMapper.kt`

- 标签从 `[anger, happiness, neutral, sadness]` 更新为 `[neutral, happiness, anger, sadness]`
- `EmotionMapper.mapSpeechEmotion()` 索引对应更新

#### 6. 模型缓存自动更新

**文件**: `TextEmotionClassifier.kt` / `SpeechEmotionClassifier.kt`

- 拷贝模型时增加文件大小比对，assets 中模型更新后自动重新拷贝

### 验证结果

```
EmotionRecognitionSvc  D  Text emotion model initialized successfully
EmotionRecognitionSvc  D  Speech emotion model initialized successfully

# 语音输入测试
EmotionRecognitionSvc  D  Speech emotion probabilities: neutral=0.236, happiness=0.254, anger=0.229, sadness=0.281
EmotionRecognitionSvc  D  Speech emotion result: SAD

# 文本输入测试（语音转文字后的文本情绪检测）
EmotionRecognitionSvc  D  Text for classification: 'A little uncomfortable'
EmotionRecognitionSvc  D  Text emotion probabilities: anger=0.007, disgust=0.329, fear=0.511, joy=0.007, neutral=0.085, sadness=0.055, surprise=0.006
EmotionRecognitionSvc  D  Text emotion result: ANXIOUS

# 文字输入测试
EmotionRecognitionSvc  D  Text for classification: 'I miss my grandson.'
EmotionRecognitionSvc  D  Text emotion probabilities: anger=0.001, disgust=0.002, fear=0.001, joy=0.002, neutral=0.006, sadness=0.986, surprise=0.002
EmotionRecognitionSvc  D  Text emotion result: SAD
```

### 情绪识别架构

```
用户输入
├── 文字输入
│   └── ChatViewModel.detectEmotionFromText()
│       └── EmotionRecognitionService.analyzeTextEmotion()
│           ├── TranslationService: 中文 → 英文（Qwen API）
│           ├── RobertaTokenizer: BPE 分词
│           ├── TextEmotionClassifier: DistilRoBERTa ONNX 推理（7类）
│           └── EmotionMapper: 7类 → 5类 App 情绪
│
└── 语音输入
    └── SpeechRecognitionService.recognize()
        ├── Qwen-Audio API: 语音转文字
        └── EmotionRecognitionService.analyzeSpeechEmotion()
            ├── AudioPreprocessor: M4A → 16kHz PCM → 预处理
            ├── SpeechEmotionClassifier: DistilHuBERT ONNX 推理（4类）
            └── EmotionMapper: 4类 → 5类 App 情绪
```
