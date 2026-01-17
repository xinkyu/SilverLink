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

    const { elderDeviceId, date } = params;

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    const query = { elderDeviceId };
    if (date) {
      query.date = date;
    }

    const { data } = await db
      .collection("medication_logs")
      .where(query)
      .orderBy("date", "desc")
      .orderBy("scheduledTime", "desc")
      .limit(100)
      .get();

    const result = data.map((item) => ({
      id: item.id_,
      medicationId: item.medicationId,
      medicationName: item.medicationName,
      dosage: item.dosage,
      scheduledTime: item.scheduledTime,
      status: item.status,
      date: item.date,
      createdAt: item.createdAt.toISOString(),
    }));

    return { success: true, data: result };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
