const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

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

    const { code, elderDeviceId } = params;

    console.log("pairing-verify 收到参数:", { code, elderDeviceId });

    if (!code || !elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 查找未过期且未配对的配对码
    const { data } = await db
      .collection("pairing_codes")
      .where({
        code,
        status: "pending",
        expiresAt: _.gt(new Date()),
      })
      .get();

    console.log("查询结果:", data);

    if (!data || data.length === 0) {
      return { success: false, message: "配对码无效或已过期" };
    }

    const pairing = data[0];
    const pairedAt = new Date();

    // 更新配对状态
    await db.collection("pairing_codes").doc(pairing._id).update({
      elderDeviceId,
      status: "paired",
      pairedAt,
    });

    return {
      success: true,
      data: {
        elderName: pairing.elderName,
        elderProfile: pairing.elderProfile || "",
        dialect: pairing.dialect || "NONE",
        clonedVoiceId: pairing.clonedVoiceId || "",
        familyDeviceId: pairing.familyDeviceId,
        pairedAt: pairedAt.toISOString(),
      },
    };
  } catch (e) {
    console.error("pairing-verify 错误:", e);
    return { success: false, message: e.message };
  }
};
