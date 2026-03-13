# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SilverLink (银龄守护) is a dual-role Android application for elderly care and family monitoring. It consists of:
- **app** module: Main phone application (elder/family modes)
- **wear** module: OPPO Watch companion app
- **shared** module: Shared Kotlin code (models, algorithms, protocols)
- **oppo-sdk-bridge** module: SDK abstraction layer with mock implementations

## Build Commands

```bash
# Build all modules
./gradlew assembleDebug

# Build specific module
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug

# Run tests
./gradlew test
./gradlew :app:test

# Lint check
./gradlew lint

# Clean build
./gradlew clean
```

## Configuration

Required in `local.properties`:
```properties
QWEN_API_KEY=your_dashscope_api_key
CLOUDBASE_URL=https://your-env-id.appid.ap-shanghai.app.tcloudbase.com/
```

## Architecture

### Dual Role System
The app operates in two modes determined by `UserPreferences.userConfig.role`:
- **ELDER**: 5 tabs - AI Companion, Medication, Memory Album, Health Records, Safety
- **FAMILY**: 2 tabs - Elder Health Monitoring, Memory Library

### Data Flow
- **Local**: Room database (`AppDatabase`) for medications, logs, chat history
- **Remote**: Retrofit + CloudBase cloud functions for pairing, sync, alerts
- **AI**: Alibaba DashScope (Qwen for chat, Qwen2-Audio for ASR, Qwen-VL for vision)

### Key Packages

**app module:**
- `data/local/` - Room entities, DAOs, database
- `data/remote/` - Retrofit APIs, CloudBase services
- `feature/chat/` - Voice recording, ASR, TTS, AI conversation
- `feature/reminder/` - Medication management, alarms, verification
- `feature/falldetection/` - Accelerometer-based fall detection, emergency alerts
- `feature/proactive/` - Activity monitoring, proactive interaction
- `feature/memory/` - Photo upload, memory quiz, cognitive assessment
- `ui/` - Compose screens organized by feature

**wear module:**
- `ui/screens/` - 7 watch screens (health dashboard, SOS, medication, sleep, heart, settings)
- `service/` - Fall detection, health monitor, communication manager
- `data/WatchPreferences.kt` - SharedPreferences wrapper for watch data

**shared module:**
- `detection/` - Fall detection algorithm (AccelerometerBuffer, FallClassifier)
- `model/` - Data models (MedicationInfo, HealthData, EmergencyContact)
- `protocol/WatchMessage.kt` - Sealed class protocol for watch-phone communication

**oppo-sdk-bridge module:**
- `health/` - HealthServiceBridge interface for heart rate, steps, sleep, SpO2
- `nearby/` - NearbyBridge interface for device discovery and messaging
- Mock implementations available for testing without real SDK

## Communication Protocol

Watch-phone communication uses `WatchMessage` sealed class (shared/protocol/WatchMessage.kt):
- `SyncMedications`, `SyncEmergencyContacts` - Phone → Watch
- `HealthDataSync`, `SOSTriggered`, `FallDetected`, `MedicationConfirmed` - Watch → Phone

## Feature Documentation

When implementing new features, create documentation in `docs/features/` following the template in `docs/ai_rules.md`.
