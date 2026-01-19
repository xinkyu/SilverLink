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
 * 添加药品（家人端为长辈添加）
 * POST medication/add
 * 
 * 请求参数:
 * - elderDeviceId: 长辈设备ID
 * - familyDeviceId: 家人设备ID
 * - name: 药品名称
 * - dosage: 剂量
 * - times: 服药时间（逗号分隔，如 "08:00,12:00,18:00"）
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);

    const { elderDeviceId, familyDeviceId, name, dosage, times } = params;

    if (!elderDeviceId || !name || !dosage || !times) {
      return { success: false, message: "参数不完整" };
    }

    // 验证家人端是否与该长辈配对
    const pairingCheck = await db
      .collection("pairing_codes")
      .where({
        elderDeviceId: elderDeviceId,
        familyDeviceId: familyDeviceId,
      })
      .get();

    if (pairingCheck.data.length === 0) {
      return { success: false, message: "未与该长辈配对", errorCode: "NOT_PAIRED" };
    }

    // 添加药品到 medications 集合
    const result = await db.collection("medications").add({
      elderDeviceId,
      familyDeviceId,
      name,
      dosage,
      times,
      createdAt: new Date().toISOString(),
      createdBy: "family",
      isActive: true,
    });

    return {
      success: true,
      data: {
        id_: result.id,
        name,
        dosage,
        times,
        createdAt: new Date().toISOString(),
        createdBy: "family",
      },
    };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
