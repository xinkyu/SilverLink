const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

/**
 * 老人端上传位置
 * 保留最近2小时内的位置记录
 */
exports.main = async (event) => {
  console.log("收到 location-update 请求, event:", JSON.stringify(event));

  try {
    // 兼容 HTTP 请求 (event.body) 和 SDK 调用 (event)
    let params = event;
    if (event.body) {
      try {
        params = JSON.parse(event.body);
        console.log("解析 HTTP Body 成功:", params);
      } catch (e) {
        console.warn("JSON解析失败", e);
      }
    }

    const { elderDeviceId, latitude, longitude, accuracy, address } = params;
    console.log("提取参数 - deviceId:", elderDeviceId, "lat:", latitude, "lng:", longitude);

    if (!elderDeviceId || latitude === undefined || longitude === undefined) {
      console.error("参数不完整");
      return { success: false, message: "参数不完整" };
    }

    const now = new Date();
    const twoHoursAgo = new Date(now.getTime() - 2 * 60 * 60 * 1000);

    // 1. 删除2小时前的旧记录
    await db
      .collection("elder_locations")
      .where({
        elderDeviceId,
        createdAt: db.command.lt(twoHoursAgo),
      })
      .remove();

    // 2. 添加新的位置记录
    const res = await db.collection("elder_locations").add({
      elderDeviceId,
      latitude,
      longitude,
      accuracy: accuracy || 0,
      address: address || "",
      createdAt: now,
    });

    console.log("位置写入数据库成功, ID:", res.id);

    return { success: true };
  } catch (e) {
    console.error("处理失败:", e);
    return { success: false, message: e.message };
  }
};
