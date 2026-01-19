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

    const { elderDeviceId, familyDeviceId, days = 7 } = params;

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 如果提供了 familyDeviceId，验证配对关系
    if (familyDeviceId) {
      const pairingCheck = await db
        .collection("pairing_codes")
        .where({
          elderDeviceId: elderDeviceId,
          familyDeviceId: familyDeviceId,
          status: "paired",
        })
        .limit(1)
        .get();

      if (!pairingCheck.data || pairingCheck.data.length === 0) {
        return {
          success: false,
          message: "未与该长辈配对",
          errorCode: "NOT_PAIRED",
        };
      }
    }

    const startDate = new Date();
    startDate.setDate(startDate.getDate() - days);
    const startDateStr = startDate.toISOString().split("T")[0];

    const { data } = await db
      .collection("mood_logs")
      .where({
        elderDeviceId,
        date: _.gte(startDateStr),
      })
      .orderBy("date", "desc")
      .orderBy("createdAt", "desc")
      .limit(100)
      .get();

    const result = data.map((item) => ({
      id: item.id_,
      mood: item.mood,
      note: item.note,
      conversationSummary: item.conversationSummary,
      date: item.date,
      createdAt: item.createdAt.toISOString(),
    }));

    return { success: true, data: result };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
