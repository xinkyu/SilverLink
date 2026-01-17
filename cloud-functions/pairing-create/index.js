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

    const { code, elderName, familyDeviceId, expiresInMinutes = 30 } = params;

    console.log("pairing-create 收到参数:", {
      code,
      elderName,
      familyDeviceId,
    });

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

    console.log("配对码创建成功");

    return {
      success: true,
      data: { code, elderName, expiresAt: expiresAt.toISOString() },
    };
  } catch (e) {
    console.error("pairing-create 错误:", e);
    return { success: false, message: e.message };
  }
};
