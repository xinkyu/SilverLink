# 腾讯云 CloudBase 云函数部署指南

## 架构说明

```
┌─────────────────┐     HTTP POST      ┌─────────────────┐     读写      ┌─────────────────┐
│   Android App   │ ──────────────────> │    云函数       │ ──────────>  │   云数据库      │
│   (Retrofit)    │ <────────────────── │   (Node.js)     │ <──────────  │  (MongoDB-like) │
└─────────────────┘     JSON Response   └─────────────────┘              └─────────────────┘
```

## 1. 创建 CloudBase 环境

1. 访问 [腾讯云 CloudBase 控制台](https://console.cloud.tencent.com/tcb)
2. 创建新环境（选择"按量计费"更灵活）
3. 记录 **环境 ID**（如 `silverlink-xxxxx`）

## 2. 创建云数据库集合

在控制台 -> 数据库 中创建以下集合：

### 集合：pairing_codes

```json
{
  "id_": "自动生成",
  "code": "123456",
  "elderName": "张奶奶",
  "familyDeviceId": "android_device_id",
  "elderDeviceId": null,
  "status": "pending",
  "expiresAt": "2024-01-17T12:30:00.000Z",
  "pairedAt": null,
  "createdAt": "2024-01-17T12:00:00.000Z"
}
```

### 集合：medication_logs

```json
{
  "id_": "自动生成",
  "elderDeviceId": "android_device_id",
  "medicationId": 1,
  "medicationName": "降压药",
  "dosage": "1片",
  "scheduledTime": "08:00",
  "status": "taken",
  "date": "2024-01-17",
  "createdAt": "2024-01-17T08:05:00.000Z"
}
```

### 集合：mood_logs

```json
{
  "id_": "自动生成",
  "elderDeviceId": "android_device_id",
  "mood": "happy",
  "note": "今天心情不错",
  "conversationSummary": "和AI聊了天气和家人",
  "date": "2024-01-17",
  "createdAt": "2024-01-17T10:00:00.000Z"
}
```

## 3. 部署云函数

在控制台 -> 云函数 中创建以下函数，运行环境选择 **Node.js 16**：

### 函数：pairing-create

```javascript
// pairing-create/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

exports.main = async (event) => {
  try {
    const { code, elderName, familyDeviceId, expiresInMinutes = 30 } = event;

    if (!code || !elderName || !familyDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 删除该家人之前的未配对记录
    await db
      .collection("pairing_codes")
      .where({ familyDeviceId, status: "pending" })
      .remove();

    const expiresAt = new Date(Date.now() + expiresInMinutes * 60 * 1000);

    await db.collection("pairing_codes").add({
      code,
      elderName,
      familyDeviceId,
      elderDeviceId: null,
      status: "pending",
      expiresAt,
      pairedAt: null,
      createdAt: new Date(),
    });

    return {
      success: true,
      data: { code, elderName, expiresAt: expiresAt.toISOString() },
    };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
```

### 函数：pairing-verify

```javascript
// pairing-verify/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

exports.main = async (event) => {
  try {
    const { code, elderDeviceId } = event;

    if (!code || !elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 查找未过期且未配对的配对码
    const { data } = await db
      .collection("pairing_codes")
      .where({
        code,
        status: "pending",
        expiresAt: _.gt(new Date()),
      })
      .get();

    if (!data || data.length === 0) {
      return { success: false, message: "配对码无效或已过期" };
    }

    const pairing = data[0];
    const pairedAt = new Date();

    // 更新配对状态
    await db.collection("pairing_codes").doc(pairing.id_).update({
      elderDeviceId,
      status: "paired",
      pairedAt,
    });

    return {
      success: true,
      data: {
        elderName: pairing.elderName,
        familyDeviceId: pairing.familyDeviceId,
        pairedAt: pairedAt.toISOString(),
      },
    };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
```

### 函数：pairing-get-elder

```javascript
// pairing-get-elder/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

exports.main = async (event) => {
  try {
    const { familyDeviceId } = event;

    if (!familyDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    const { data } = await db
      .collection("pairing_codes")
      .where({ familyDeviceId, status: "paired" })
      .orderBy("pairedAt", "desc")
      .limit(1)
      .get();

    if (!data || data.length === 0) {
      return { success: true, data: null };
    }

    return { success: true, data: data[0].elderDeviceId };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
```

### 函数：medication-log

```javascript
// medication-log/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

exports.main = async (event) => {
  try {
    const {
      elderDeviceId,
      medicationId,
      medicationName,
      dosage,
      scheduledTime,
      status,
      date,
    } = event;

    if (
      !elderDeviceId ||
      !medicationId ||
      !medicationName ||
      !scheduledTime ||
      !status
    ) {
      return { success: false, message: "参数不完整" };
    }

    const logDate = date || new Date().toISOString().split("T")[0];

    await db.collection("medication_logs").add({
      elderDeviceId,
      medicationId,
      medicationName,
      dosage: dosage || "",
      scheduledTime,
      status,
      date: logDate,
      createdAt: new Date(),
    });

    return { success: true };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
```

### 函数：medication-query

```javascript
// medication-query/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

exports.main = async (event) => {
  try {
    const { elderDeviceId, date } = event;

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    const query = { elderDeviceId };
    if (date) {
      query.date = date;
    }

    const { data } = await db
      .collection("medication_logs")
      .where(query)
      .orderBy("date", "desc")
      .orderBy("scheduledTime", "desc")
      .limit(100)
      .get();

    const result = data.map((item) => ({
      id: item.id_,
      medicationId: item.medicationId,
      medicationName: item.medicationName,
      dosage: item.dosage,
      scheduledTime: item.scheduledTime,
      status: item.status,
      date: item.date,
      createdAt: item.createdAt.toISOString(),
    }));

    return { success: true, data: result };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
```

### 函数：mood-log

```javascript
// mood-log/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

exports.main = async (event) => {
  try {
    const { elderDeviceId, mood, note, conversationSummary, date } = event;

    if (!elderDeviceId || !mood) {
      return { success: false, message: "参数不完整" };
    }

    const logDate = date || new Date().toISOString().split("T")[0];

    await db.collection("mood_logs").add({
      elderDeviceId,
      mood,
      note: note || "",
      conversationSummary: conversationSummary || "",
      date: logDate,
      createdAt: new Date(),
    });

    return { success: true };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
```

### 函数：mood-query

```javascript
// mood-query/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

exports.main = async (event) => {
  try {
    const { elderDeviceId, days = 7 } = event;

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    const startDate = new Date();
    startDate.setDate(startDate.getDate() - days);
    const startDateStr = startDate.toISOString().split("T")[0];

    const { data } = await db
      .collection("mood_logs")
      .where({
        elderDeviceId,
        date: _.gte(startDateStr),
      })
      .orderBy("date", "desc")
      .orderBy("createdAt", "desc")
      .limit(100)
      .get();

    const result = data.map((item) => ({
      id: item.id_,
      mood: item.mood,
      note: item.note,
      conversationSummary: item.conversationSummary,
      date: item.date,
      createdAt: item.createdAt.toISOString(),
    }));

    return { success: true, data: result };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
```

## 4. 配置 HTTP 访问服务

1. 在控制台 -> HTTP 访问服务 中开启服务
2. 添加触发路径映射：

| 路径               | 关联云函数        |
| ------------------ | ----------------- |
| /pairing/create    | pairing-create    |
| /pairing/verify    | pairing-verify    |
| /pairing/get-elder | pairing-get-elder |
| /medication/log    | medication-log    |
| /medication/query  | medication-query  |
| /mood/log          | mood-log          |
| /mood/query        | mood-query        |
| /emergency/report  | emergency-report  |
| /emergency/query   | emergency-query   |
| /emergency/resolve | emergency-resolve |

3. 获取访问地址，格式为：`https://<环境ID>.service.tcloudbase.com/`

## 5. 更新 Android 配置

在 `CloudBaseService.kt` 中更新：

```kotlin
private const val CLOUD_BASE_URL = "https://<你的环境ID>.service.tcloudbase.com/"
```

## 6. 测试

使用 curl 或 Postman 测试：

```bash
# 创建配对码
curl -X POST https://<环境ID>.service.tcloudbase.com/pairing/create \
  -H "Content-Type: application/json" \
  -d '{"code":"123456","elderName":"张奶奶","familyDeviceId":"test_family_001"}'

# 验证配对码
curl -X POST https://<环境ID>.service.tcloudbase.com/pairing/verify \
  -H "Content-Type: application/json" \
  -d '{"code":"123456","elderDeviceId":"test_elder_001"}'
```

## 费用说明

CloudBase 按量计费模式：

- 云函数：调用次数 + 资源使用量
- 云数据库：读写次数 + 存储容量
- HTTP 访问服务：请求次数

对于家庭使用场景，月费用预计在 **几元到几十元** 之间。
