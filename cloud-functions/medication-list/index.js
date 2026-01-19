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
 * 获取药品列表
 * POST medication/list
 * 
 * 请求参数:
 * - elderDeviceId: 长辈设备ID
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);

    const { elderDeviceId } = params;

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 查询该长辈的所有药品
    const result = await db
      .collection("medications")
      .where({
        elderDeviceId: elderDeviceId,
        isActive: true,
      })
      .orderBy("createdAt", "desc")
      .get();

    const medications = result.data.map((med) => ({
      id_: med._id,
      name: med.name,
      dosage: med.dosage,
      times: med.times,
      createdAt: med.createdAt,
      createdBy: med.createdBy || "elder",
    }));

    return {
      success: true,
      data: medications,
    };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
