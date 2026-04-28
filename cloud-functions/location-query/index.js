const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

function getParams(event) {
  if (!event) return {};

  let params = {};

  // CloudBase 网关常见字段：queryStringParameters
  if (
    event.queryStringParameters &&
    typeof event.queryStringParameters === "object"
  ) {
    params = { ...params, ...event.queryStringParameters };
  }

  // 兼容部分网关/运行时字段：querystring
  if (event.querystring && typeof event.querystring === "object") {
    params = { ...params, ...event.querystring };
  }

  // 兼容 event.params.query
  if (
    event.params &&
    event.params.query &&
    typeof event.params.query === "object"
  ) {
    params = { ...params, ...event.params.query };
  }

  // 兼容 POST body（字符串或对象）
  if (event.body) {
    try {
      let rawBody = event.body;
      if (event.isBase64Encoded && typeof rawBody === "string") {
        rawBody = Buffer.from(rawBody, "base64").toString("utf8");
      }
      const body = typeof rawBody === "string" ? JSON.parse(rawBody) : rawBody;
      if (body && typeof body === "object") {
        params = { ...params, ...body };
      }
    } catch (e) {
      console.warn("location-query JSON解析失败", e);
    }
  }

  // 兜底：直接从 event 取
  if (!params.elderDeviceId && event.elderDeviceId) {
    params.elderDeviceId = event.elderDeviceId;
  }
  if (!params.familyDeviceId && event.familyDeviceId) {
    params.familyDeviceId = event.familyDeviceId;
  }

  return params;
}

/**
 * 家人端查询老人位置
 * 返回最近2小时内的位置历史
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);

    const { elderDeviceId, familyDeviceId } = params;
    console.log("location-query parsed params:", {
      elderDeviceId,
      familyDeviceId,
    });

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 可选：验证家人是否有权限查看该老人的位置
    // 如果需要权限验证，可以查询 pairing_codes 表

    const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000);

    // 查询最近2小时内的位置记录，按时间倒序
    const { data } = await db
      .collection("elder_locations")
      .where({
        elderDeviceId,
        createdAt: db.command.gte(twoHoursAgo),
      })
      .orderBy("createdAt", "desc")
      .limit(50)
      .get();

    if (!data || data.length === 0) {
      return {
        success: true,
        data: {
          latest: null,
          history: [],
        },
      };
    }

    // 格式化返回数据
    const history = data.map((item) => ({
      id: item._id,
      latitude: item.latitude,
      longitude: item.longitude,
      accuracy: item.accuracy || 0,
      address: item.address || "",
      createdAt: item.createdAt
        ? item.createdAt.toISOString
          ? item.createdAt.toISOString()
          : new Date(item.createdAt).toISOString()
        : null,
    }));

    return {
      success: true,
      data: {
        latest: history[0] || null,
        history: history,
      },
    };
  } catch (e) {
    return { success: false, message: e.message };
  }
};
