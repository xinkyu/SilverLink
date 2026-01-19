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

/**
 * 查询警报云函数
 * 家人端调用，获取未读警报列表
 * 
 * 参数:
 * - familyDeviceId: 家人设备ID
 * - unreadOnly: 是否只查询未读（默认true）
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);
    const { familyDeviceId, unreadOnly = true } = params;

    console.log("alert-query 收到参数:", { familyDeviceId, unreadOnly });

    if (!familyDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 构建查询条件
    let query = { familyDeviceId };
    if (unreadOnly) {
      query.isRead = false;
    }

    // 查询警报，按创建时间倒序
    const { data } = await db
      .collection("alerts")
      .where(query)
      .orderBy("createdAt", "desc")
      .limit(50)
      .get();

    console.log("查询到警报数量:", data ? data.length : 0);

    // 格式化返回数据
    const alerts = (data || []).map((alert) => ({
      id: alert._id,
      alertType: alert.alertType,
      message: alert.message,
      elderName: alert.elderName || "",
      elderDeviceId: alert.elderDeviceId,
      isRead: alert.isRead || false,
      createdAt: alert.createdAt instanceof Date 
        ? alert.createdAt.toISOString() 
        : String(alert.createdAt),
    }));

    return {
      success: true,
      data: alerts,
    };
  } catch (e) {
    console.error("alert-query 错误:", e);
    return { success: false, message: e.message };
  }
};
