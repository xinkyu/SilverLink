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
 * 更新药品时间（长辈端/家人端）
 * POST medication/update
 *
 * 请求参数:
 * - elderDeviceId: 长辈设备ID
 * - name: 药品名称
 * - dosage: 剂量
 * - times: 服药时间（逗号分隔）
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);
    const { elderDeviceId, name, dosage, times } = params;

    if (!elderDeviceId || !name || !dosage || !times) {
      return { success: false, message: "参数不完整" };
    }

    const { data } = await db
      .collection("medications")
      .where({
        elderDeviceId,
        name,
        dosage,
        isActive: true,
      })
      .limit(1)
      .get();

    if (!data || data.length === 0) {
      return { success: false, message: "未找到药品" };
    }

    const med = data[0];
    await db.collection("medications").doc(med._id).update({
      times,
      updatedAt: new Date().toISOString(),
    });

    return {
      success: true,
      data: {
        id_: med._id,
        name: med.name,
        dosage: med.dosage,
        times,
      },
    };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
