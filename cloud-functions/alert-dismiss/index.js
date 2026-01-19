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
 * 标记警报已读云函数
 * 家人端调用，将警报标记为已读
 * 
 * 参数:
 * - alertId: 警报ID
 * - familyDeviceId: 家人设备ID（用于权限验证）
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);
    const { alertId, familyDeviceId } = params;

    console.log("alert-dismiss 收到参数:", { alertId, familyDeviceId });

    if (!alertId || !familyDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 验证该警报是否属于此家人设备
    const { data } = await db
      .collection("alerts")
      .doc(alertId)
      .get();

    if (!data || data.length === 0) {
      return { success: false, message: "警报不存在" };
    }

    const alert = Array.isArray(data) ? data[0] : data;
    
    if (alert.familyDeviceId !== familyDeviceId) {
      return { success: false, message: "无权操作此警报" };
    }

    // 更新为已读
    await db.collection("alerts").doc(alertId).update({
      isRead: true,
      readAt: new Date(),
    });

    console.log("警报已标记为已读:", alertId);

    return { success: true };
  } catch (e) {
    console.error("alert-dismiss 错误:", e);
    return { success: false, message: e.message };
  }
};
