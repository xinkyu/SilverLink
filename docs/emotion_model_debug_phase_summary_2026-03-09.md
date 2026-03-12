# 端侧情绪识别排查阶段总结（2026-03-09）

## 1. 背景与目标
- 目标: 验证 SilverLink Android 端 MemoCMT 情绪识别模型在真实语音场景下是否正确工作。
- 现象: 初期表现为无论输入文本/语音，结果大量塌缩到 `Sad`。

## 2. 阶段性关键结论
- 结论1: Android 端模型加载链路是通的。
	- 已确认 `EmotionRecognitionSvc` 正常初始化。
	- 已确认加载模型文件为 `model_mobile.onnx`，大小 `395565116 bytes`。
- 结论2: `text-only` 路径本身在当前模型上不可靠。
	- 同一文本在 `audio=0` 条件下，云端与手机端均可复现 `Sad` 偏置。
	- 说明问题不是端侧接入错，而是该模型在 text-only 退化路径上的行为。
- 结论3: 语音链路曾被业务流程中的 text-only 二次判别覆盖。
	- 语音识别后先得到 cross-modal 情绪，再被 `sendMessage` 中 text-only 重算覆盖。
	- 已修复为语音链路可跳过 text-only 复判。
- 结论4: 手机端与云端可实现输入对齐（token/audio/logits）。
	- 在同文本、同音频（导出后）条件下，token 头、音频头、logits 可高度一致。
	- 说明 ONNX Runtime 输入类型/端序/输出提取已基本排干净。

## 3. 已完成排查与验证

### 3.1 云端模型对比验证
- FP32 在云端验证集表现正常（高准确率）。
- INT8 在同集上明显退化（低准确率，类别偏置明显）。
- 结论: 量化模型存在显著性能风险，不宜直接用于端侧判断真实情绪。

### 3.2 Android 端输入链路验证
- 音频解码日志确认:
	- `mime=audio/mp4a-latm`
	- `sampleRate=16000`
	- `channels=1`
	- 固定长度 `160220`。
- tokenizer 日志确认:
	- vocab 加载成功 (`30522`)。
	- special token IDs: `PAD=0, UNK=100, CLS=101, SEP=102`。

### 3.3 同输入云端复现
- 通过导出手机录音并在云端推理，验证同文本同音频的输出与端侧一致。
- 排除“仅端侧有 bug”的单点假设。

## 4. 已落地代码改动

### 4.1 模型与推理侧
- 文件: `app/src/main/java/com/silverlink/app/feature/emotion/MemoCmtClassifier.kt`
- 改动:
	- 切换加载 `model_mobile.onnx`（FP32）用于验证。
	- 增加模型配置/文件大小/会话输入输出名日志。
	- 明确从 `output='logits'` 读取输出，避免误取其他输出。
	- 增加 `Raw logits` 日志。
	- 使用 `ByteBuffer.allocateDirect(...).order(ByteOrder.nativeOrder())` 构建 text/audio tensor，明确 `int64/float32` 类型与内存布局。
	- 增加输入头日志: `Input text head(10)`、`Input audio head(8)`。

### 4.2 tokenizer 对齐
- 文件: `app/src/main/java/com/silverlink/app/feature/emotion/WordPieceTokenizer.kt`
- 改动:
	- special token ID 从 vocab 动态读取，不再完全硬编码。
	- 增加 special token ID 日志。

### 4.3 音频预处理增强
- 文件: `app/src/main/java/com/silverlink/app/feature/emotion/AudioPreprocessor.kt`
- 改动:
	- 修复 MediaCodec output buffer 读取边界（`offset/size`）。
	- 增加波形统计日志（rms/peak）。
	- 新增 VAD-like 首尾静音裁剪（`top_db=25` 近似实现）。
	- 新增 peak normalization（峰值归一化）。
	- 增加 `decodedLen/trimmedLen/finalRms/finalPeak` 诊断日志。

### 4.4 业务流程修复
- 文件: `app/src/main/java/com/silverlink/app/ui/chat/ChatViewModel.kt`
- 改动:
	- `sendMessage` 新增参数 `skipTextEmotionDetection`。
	- 语音链路调用 `sendMessage(..., skipTextEmotionDetection = true)`，避免 cross-modal 结果被 text-only 覆盖。

### 4.5 诊断日志补充
- 文件: `app/src/main/java/com/silverlink/app/feature/emotion/EmotionRecognitionService.kt`
- 改动:
	- 增加 token 统计日志: `nonPad`、`unk`、`head`，便于与云端逐项对齐。

## 5. 当前风险评估
- 风险1: `text-only` 结果在当前模型上不稳定/偏置，不能作为主要情绪信号。
- 风险2: 融合策略可能对音频分支过于敏感（已观察到低能量真实语音下偏 Sad）。
- 风险3: INT8 模型退化明显，短期不建议替代 FP32。

## 6. 建议的下一阶段动作
- 动作1: 产品逻辑层面明确策略。
	- 语音输入: 使用 cross-modal。
	- 纯文本输入: 暂不使用 MemoCMT text-only，改关键词/云端文本情绪。
- 动作2: 训练侧进行融合策略 A/B。
	- 对比 `min` 与 `mean/concat` 融合头。
	- 使用手机真实录音集进行验证，而非仅实验室验证集。
- 动作3: 建立端云一致性回归。
	- 固定样本集上对比: token head、audio head、logits、预测类别。
	- 将该回归纳入发布前检查。
- 动作4: 量化模型重做评估。
	- 仅当 INT8 在真实录音集接近 FP32 时再考虑上端。

## 7. 一句话结论
- 当前阶段已基本排除 Android 接入错误，问题核心从“端侧实现”转向“模型在真实场景分布下的行为与融合策略”。
