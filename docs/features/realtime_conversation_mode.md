# 实时对话模式 (Real-time Conversational Mode)

## 目标

将现有“按住录音 -> 识别 -> 回复”的语音链路升级为类似打电话的实时对话模式，支持端侧 VAD 自动开口检测、全双工打断以及清晰的状态机管理。

## 核心能力

- 端侧 VAD：使用 WebRTC VAD 对 16kHz PCM 帧实时检测说话起止。
- 全双工与打断：AI 正在播报时检测到用户开口，立即停止 TTS 播放并进入监听。
- 状态机：Idle / Listening / Processing / Speaking / Interrupted / Error。
- 低延迟：建议引入流式 ASR/TTS，优先播放第一句，减少等待感。
- **回声消除 (AEC)**：通过共享 Audio Session ID 和软件算法，防止 AI 语音被误识别为用户输入。

## 回声消除方案实现 (Echo Cancellation)

为了解决 AI 播放语音时被麦克风重新采集导致“自己对话自己”的问题，系统采取了硬件+软件双重保障：

### 1. 硬件级回声消除 (Hardware AEC)
- **共享 Audio Session**：`AudioRecord`（录音）和 `AudioTrack`（播放）共享同一个 `audioSessionId`。
- **PcmAudioPlayer**：弃用 `MediaPlayer`，使用基于 `AudioTrack` 的自定义播放器 `PcmAudioPlayer`，支持显式设置 Session ID。
- **系统支持**：通过共享 Session ID，Android 系统的 `AcousticEchoCanceler` 能够准确识别设备输出的音频并从输入信号中消除。

### 2. 软件级过滤优化
- **高阈值打断**：在 AI 播放期间，将 VAD 的打断阈值 (`bargeInRmsThreshold`) 提高，避免低音量的回声触发打断。
- **连续帧确认**：要求连续多帧（如 3 帧）检测到有效语音才触发打断，过滤瞬态噪声。
- **播放冷却期**：AI 开始播放的前 500ms 内禁用打断检测，防止播放初期的瞬间高音量造成误触发。

## 流式 ASR 方案（Qwen3-ASR-Flash-Realtime）

- 接入方式：WebSocket API（北京地域 `wss://dashscope.aliyuncs.com/api-ws/v1/realtime`）。
- 音频格式：PCM 16kHz / 16bit / mono。
- 建议模式：端侧 VAD + Manual Commit。
  - 端侧 VAD 负责 speechStart/speechEnd。
  - 发送 `input_audio_buffer.append` 持续上传 PCM。
  - speechEnd 后发送 `input_audio_buffer.commit` 与 `session.finish`。

## 流式 TTS 方案（CosyVoice）

- 推荐模型：cosyvoice-v3-flash（低延迟、成本低）。
- 播放策略：边接收音频帧边播放，首包即播，避免整句等待。

## 状态机定义

- Idle：无对话输入，待机。
- Listening：VAD 已检测到人声或正在持续监听。
- Processing：收到语音结束信号，进行 ASR 与模型请求。
- Speaking：AI 正在播放语音。
- Interrupted：AI 播放中被用户打断。
- Error：录音或流式链路异常。

状态转移概览：

- Idle -> Listening：VAD 触发 speechStart。
- Listening -> Processing：VAD 触发 speechEnd 或静默超时。
- Processing -> Speaking：AI 回复生成，TTS 开始播放。
- Speaking -> Listening：VAD 触发 speechStart，打断并进入监听。
- Speaking -> Idle：播放结束且无输入。

## 打断逻辑流程 (文字流程图)

1) VAD 监听中，AI 处于 Speaking。
2) 用户打断，触发 VAD speechStart（需满足高 RMS 阈值与连续帧要求）。
3) 进入 Interrupted：立即 stop 播放器，取消当前 TTS 播放队列。
4) 切换状态到 Listening，启动 ASR 缓冲与流式上送。
5) VAD 触发 speechEnd，进入 Processing。
6) ASR 完成，发送文本到模型，收到回复后进入 Speaking。

## 端侧 VAD 实现建议

- 采样率：16kHz，单声道，PCM 16-bit。
- 帧长：20ms (320 samples)。
- 模式：VERY_AGGRESSIVE，减少误触发。
- 静默阈值：300~600ms 作为 speechEnd 判断。

## 低延迟建议

- 流式 ASR：支持 partial 文本回传，UI 实时展示“正在听”。
- 流式 TTS：边生成边播放，优先输出首句再逐句合成。
- TTS 预缓冲：200~400ms，兼顾流畅度与响应性。
- 文本切句：长回答按句拆分，尽早播放首句。

## 异常与回退

- VAD/录音失败：回退到手动按住录音模式。
- ASR 失败：提示“没听清，请再说一遍”，自动回 Listening。
- TTS 失败：仅展示文本回复，进入 Listening。
