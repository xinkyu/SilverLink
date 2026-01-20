const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});

exports.main = async (event, context) => {
  try {
    const { familyDeviceId, format = "wav" } =
      event.body ? JSON.parse(event.body) : event;

    if (!familyDeviceId) {
      return { success: false, message: "缺少 familyDeviceId" };
    }

    // 生成唯一文件名
    const timestamp = Date.now();
    const randomStr = Math.random().toString(36).substr(2, 9);
    const fileName = `voice_${timestamp}_${randomStr}.${format}`;
    const cloudPath = `voice_cloning/${familyDeviceId}/${fileName}`;
    const contentType =
      format === "mp3"
        ? "audio/mpeg"
        : format === "m4a"
        ? "audio/mp4"
        : "audio/wav";

    // 获取上传凭证
    // 重要：使用 getUploadMetadata 获取 PUT 签名授权
    const result = await app.getUploadMetadata({
      cloudPath: cloudPath,
      contentType: contentType,
    });

    console.log("Credentials result:", JSON.stringify(result));

    // 兼容 SDK 返回格式差异
    const meta = result.data || result;

    if (!meta || !meta.url) {
      return { success: false, message: "无法获取上传凭证" };
    }

    // 从上传 URL 中提取 COS 域名和路径，构建直接访问 URL
    // 上传 URL 格式: https://7369-xxx.cos.ap-shanghai.myqcloud.com/path/file.m4a
    // 直接访问 URL 需要 COS 桶设置为公共读取
    let directUrl = null;
    try {
      const uploadUrl = new URL(meta.url);
      // 构建不带签名参数的直接访问 URL
      directUrl = `${uploadUrl.protocol}//${uploadUrl.host}/${cloudPath}`;
    } catch (e) {
      console.error("Failed to parse upload URL:", e);
    }

    return {
      success: true,
      data: {
        uploadUrl: meta.url,
        authorization: meta.authorization,
        token: meta.token,
        fileId: meta.fileId,
        cosFileId: meta.cosFileId,
        cloudPath: cloudPath,
        contentType: contentType,
        // 直接 COS URL（需要桶设置为公共读取）
        directUrl: directUrl,
      },
    };
  } catch (error) {
    console.error("Get voice credentials error:", error);
    return {
      success: false,
      message: error.message || "获取凭证失败",
    };
  }
};
