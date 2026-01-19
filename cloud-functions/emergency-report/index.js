const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

function getParams(event) {
  if (!event) return {};
  if (event.body) {
    let body = event.body;
    if (event.isBase64Encoded) {
      body = Buffer.from(body, "base64").toString("utf8");
    }
    if (typeof body === "string") {
      try {
        return JSON.parse(body);
      } catch (e) {
        return {};
      }
    }
    return body || {};
  }
  if (event.queryStringParameters) {
    return event.queryStringParameters;
  }
  return event;
}

/**
 * 上报紧急事件（老人端跌倒时调用）
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);
    const { elderDeviceId, eventType = "fall", latitude, longitude, timestamp } = params;

    console.log("emergency-report 收到参数:", {
      elderDeviceId,
      eventType,
      latitude,
      longitude,
      timestamp,
    });

    if (!elderDeviceId) {
      return { success: false, message: "缺少 elderDeviceId 参数" };
    }

    // 获取老人名称（从配对记录中查找）
    let elderName = "";
    try {
      const pairingResult = await db
        .collection("pairing_codes")
        .where({ elderDeviceId, status: "paired" })
        .limit(1)
        .get();
      if (pairingResult.data && pairingResult.data.length > 0) {
        elderName = pairingResult.data[0].elderName || "";
      }
    } catch (e) {
      console.log("获取老人名称失败:", e);
    }

    // 创建紧急事件记录
    const eventData = {
      elderDeviceId,
      elderName,
      eventType,
      latitude: latitude || null,
      longitude: longitude || null,
      timestamp: timestamp || Date.now(),
      resolved: false,
      resolvedAt: null,
      resolvedBy: null,
      createdAt: new Date(),
    };

    const result = await db.collection("emergency_events").add(eventData);

    console.log("紧急事件创建成功:", result.id);

    return {
      success: true,
      data: {
        id: result.id,
        ...eventData,
      },
    };
  } catch (e) {
    console.error("emergency-report 错误:", e);
    return { success: false, message: e.message };
  }
};
