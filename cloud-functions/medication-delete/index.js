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
 * 删除药品
 * POST medication/delete
 * 
 * 请求参数:
 * - elderDeviceId: 长辈设备ID
 * - medicationId: 药品ID
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);

    const { elderDeviceId, medicationId } = params;

    if (!elderDeviceId || !medicationId) {
      return { success: false, message: "参数不完整" };
    }

    // 软删除：将 isActive 设为 false
    await db
      .collection("medications")
      .doc(medicationId)
      .update({
        isActive: false,
        deletedAt: new Date().toISOString(),
      });

    return { success: true };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
