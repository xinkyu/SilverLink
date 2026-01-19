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
 * 发送警报云函数
 * 老人端调用，向配对的家人端发送警报
 * 
 * 参数:
 * - elderDeviceId: 老人设备ID
 * - alertType: 警报类型 ("inactivity" | "sos" | "medication_missed")
 * - message: 警报消息
 * - elderName: 老人称呼（可选）
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);
    const { elderDeviceId, alertType, message, elderName } = params;

    console.log("alert-send 收到参数:", { elderDeviceId, alertType, message, elderName });

    if (!elderDeviceId || !alertType || !message) {
      return { success: false, message: "参数不完整" };
    }

    // 查找此老人设备对应的家人设备ID（通过配对记录）
    const { data: pairingData } = await db
      .collection("pairing_codes")
      .where({
        elderDeviceId,
        status: "paired",
      })
      .get();

    if (!pairingData || pairingData.length === 0) {
      console.log("未找到配对记录，警报无法发送");
      return { success: false, message: "未找到配对的家人设备" };
    }

    const familyDeviceId = pairingData[0].familyDeviceId;
    const resolvedElderName = elderName || pairingData[0].elderName || "老人";

    console.log("找到配对的家人设备:", familyDeviceId);

    // 创建警报记录
    const alertDoc = {
      elderDeviceId,
      familyDeviceId,
      alertType,
      message,
      elderName: resolvedElderName,
      isRead: false,
      createdAt: new Date(),
    };

    const result = await db.collection("alerts").add(alertDoc);

    console.log("警报已创建:", result.id);

    return {
      success: true,
      data: {
        alertId: result.id,
        familyDeviceId,
      },
    };
  } catch (e) {
    console.error("alert-send 错误:", e);
    return { success: false, message: e.message };
  }
};
