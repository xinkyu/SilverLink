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

    const { elderDeviceId, mood, note, conversationSummary, date } = params;

    if (!elderDeviceId || !mood) {
      return { success: false, message: "参数不完整" };
    }

    const logDate = date || new Date().toISOString().split("T")[0];

    await db.collection("mood_logs").add({
      elderDeviceId,
      mood,
      note: note || "",
      conversationSummary: conversationSummary || "",
      date: logDate,
      createdAt: new Date(),
    });

    return { success: true };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
