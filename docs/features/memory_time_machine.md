# 多模态记忆回溯功能 - 开发交接文档

> 📅 更新时间: 2026-01-20
> 🎯 功能目标: 实现 Memory Time-Machine (记忆时光机)

---

## 1. 功能概述

基于 **Qwen-VL + CloudBase** 的多模态记忆回溯功能：

- **Photo Talk**: 家人上传老照片，老人可浏览并与 AI 问答
- **Digital Amnesia Defense**: 定期展示照片测试老人认知能力

---

## 2. 已完成工作 ✅

### 2.1 数据层
- `data/local/entity/MemoryPhotoEntity.kt` - 照片本地缓存
- `data/local/entity/CognitiveLogEntity.kt` - 认知记录实体
- `data/local/dao/CognitiveLogDao.kt` - 认知记录 DAO
- `data/remote/CloudBaseApi.kt` - 照片/认知 API

### 2.2 云函数
| 函数 | 功能 | HTTP 路由 |
|------|------|-----------|
| `memory-photo-upload` | 上传照片 | `/memory-photo-upload` |
| `memory-photo-list` | 照片列表 | `/memory-photo-list` |
| `cognitive-log` | 记录测试结果 | `/cognitive-log` |
| `cognitive-report` | 生成报告 | `/cognitive-report` |

### 2.3 UI 界面
- `ui/memory/MemoryQuizScreen.kt` - 认知测验
- `ui/memory/MemoryQuizViewModel.kt` - 语音播放修复 ✅
- `ui/memory/ElderPhotoGridScreen.kt` - 老人端网格视图✅
- `ui/family/FamilyMonitoringScreen.kt` - 认知报告 Tab✅
- `ui/components/HealthRecordComponents.kt` - CognitiveReportCard
✅
---

## 3. 待修复问题 ⚠️ 

### 家人端老人端双端认知评估均因拥挤错行

### 长辈端认知评估无数据，也无没数据时的默认卡片

---

## 4. 数据流

```
老人答题 → CognitiveQuizService.saveQuizResult()
         ├─ 本地: CognitiveLogDao.insert()
         └─ 云端: CloudBaseService.logCognitiveResult()
                            ↓
               cognitive_logs 集合 (CloudBase)
                            ↓
家人查看 ← FamilyMonitoringScreen ← SyncRepository.getCognitiveReport()
```

---

## 5. 测试清单

- [x] 记忆小游戏语音反馈播放
- [x] 家人端"认知评估"Tab 显示
- [x] 认知结果上传
- [x] 家人端查看报告数据

---


