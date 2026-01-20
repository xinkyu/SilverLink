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
 * 合并时间字符串，去重并排序
 */
function mergeTimes(existingTimes, newTimes) {
  const existingArr = existingTimes.split(",").map(t => t.trim()).filter(t => t);
  const newArr = newTimes.split(",").map(t => t.trim()).filter(t => t);
  const merged = [...new Set([...existingArr, ...newArr])].sort();
  return merged.join(",");
}

/**
 * 添加药品
 * POST medication/add
 * 
 * 支持两种场景：
 * 1. 家人端为长辈添加：需要 elderDeviceId 和 familyDeviceId
 * 2. 老人端自己添加：只需要 elderDeviceId（此时作为自己的设备ID）
 * 
 * 如果已存在同名同剂量的药品，则合并时间点而非创建新记录
 * 
 * 请求参数:
 * - elderDeviceId: 长辈设备ID
 * - familyDeviceId: 家人设备ID（可选，老人端自己添加时不需要）
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

    let createdBy = "elder";

    // 如果提供了 familyDeviceId，验证家人端是否与该长辈配对
    if (familyDeviceId) {
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
      createdBy = "family";
    }

    // 检查是否已存在同名同剂量的药品
    const existingMeds = await db
      .collection("medications")
      .where({
        elderDeviceId,
        name: name.trim(),
        dosage: dosage.trim(),
        isActive: true,
      })
      .get();

    if (existingMeds.data.length > 0) {
      // 已存在，合并时间点到第一个匹配的记录
      const existingMed = existingMeds.data[0];
      const mergedTimes = mergeTimes(existingMed.times || "", times);

      await db.collection("medications").doc(existingMed._id).update({
        times: mergedTimes,
        updatedAt: new Date().toISOString(),
      });

      return {
        success: true,
        data: {
          id_: existingMed._id,
          name: existingMed.name,
          dosage: existingMed.dosage,
          times: mergedTimes,
          createdAt: existingMed.createdAt,
          createdBy: existingMed.createdBy,
          merged: true,
        },
      };
    }

    // 不存在，创建新记录
    const result = await db.collection("medications").add({
      elderDeviceId,
      familyDeviceId: familyDeviceId || "",
      name: name.trim(),
      dosage: dosage.trim(),
      times,
      createdAt: new Date().toISOString(),
      createdBy,
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
        createdBy,
      },
    };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
