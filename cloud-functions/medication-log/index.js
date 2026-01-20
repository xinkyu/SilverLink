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

exports.main = async (event) => {
  try {
    // HTTP 触发时，参数在 body 中
    const params = getParams(event);

    const {
      elderDeviceId,
      medicationId,
      medicationName,
      dosage,
      scheduledTime,
      status,
      date,
    } = params;

    if (
      !elderDeviceId ||
      !medicationId ||
      !medicationName ||
      !scheduledTime ||
      !status
    ) {
      return { success: false, message: "参数不完整" };
    }

    // 使用中国时区 (GMT+8) 计算日期
    const now = new Date();
    const chinaOffset = 8 * 60 * 60 * 1000; // UTC+8
    const chinaTime = new Date(now.getTime() + chinaOffset);
    const logDate = date || chinaTime.toISOString().split("T")[0];

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
