const cloud = require("@cloudbase/node-sdk");

// 初始化
const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

exports.main = async (event, context) => {
  try {
    // 1. 解析参数
    let body = event.body || {};
    if (event.isBase64Encoded) {
      body = Buffer.from(body, "base64").toString("utf8");
    }
    if (typeof body === "string") {
      try {
        body = JSON.parse(body);
      } catch (e) {}
    }
    const params = { ...(event.queryStringParameters || {}), ...body };
    const { elderDeviceId, familyDeviceId, fileExtension = "jpg" } = params;

    // 2. 校验参数
    if (!elderDeviceId || !familyDeviceId) {
      return { success: false, message: "缺少必要参数" };
    }

    // 3. 校验配对
    const countResult = await db
      .collection("pairing_codes")
      .where({
        familyDeviceId,
        elderDeviceId,
        status: "paired",
      })
      .count();

    if (countResult.total === 0) {
      return { success: false, message: "未找到配对关系" };
    }

    // 4. 准备路径
    const photoId = `photo_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const cloudPath = `memory_photos/${elderDeviceId}/${photoId}.${fileExtension}`;
    const contentType = `image/${fileExtension === "png" ? "png" : "jpeg"}`;

    // 5. 核心修复：获取并解包数据
    const result = await app.getUploadMetadata({
      cloudPath: cloudPath,
      contentType: contentType,
    });

    console.log("SDK原始返回:", JSON.stringify(result));

    // 关键修正：兼容 result.data 和直接 result 的情况
    const meta = result.data || result;

    if (!meta || !meta.url) {
      return { success: false, message: "凭证字段缺失" };
    }

    // 6. 返回成功
    return {
      success: true,
      data: {
        photoId,
        cloudPath,
        uploadUrl: meta.url,
        authorization: meta.authorization,
        token: meta.token,
        fileId: meta.fileId,
        cosFileId: meta.cosFileId,
        contentType,
      },
    };
  } catch (e) {
    console.error("系统错误:", e);
    return {
      success: false,
      message: `服务端内部错误: ${e.message}`,
    };
  }
};
