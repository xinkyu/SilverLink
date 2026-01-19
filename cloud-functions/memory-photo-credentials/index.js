// memory-photo-credentials/index.js
// 生成 COS 预签名上传 URL
const cloud = require("@cloudbase/node-sdk");
const COS = require("cos-nodejs-sdk-v5");
const STS = require("qcloud-cos-sts");

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

/**
 * 生成 COS 上传凭证
 * 方案：使用 CloudBase uploadFile 先上传一个占位文件获取 fileId，
 * 然后返回 fileId 和临时访问 URL
 * 
 * 注意：由于 CloudBase 云函数内无法直接生成 COS 预签名 URL，
 * 我们改为在云函数中完成上传，但接受 multipart 格式的请求
 */
exports.main = async (event) => {
  try {
    const { elderDeviceId, familyDeviceId, fileExtension = "jpg" } =
      getParams(event);

    if (!elderDeviceId || !familyDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 验证配对关系
    const { data: pairings } = await db
      .collection("pairing_codes")
      .where({
        familyDeviceId,
        elderDeviceId,
        status: "paired",
      })
      .limit(1)
      .get();

    if (!pairings || pairings.length === 0) {
      return { success: false, message: "未找到配对关系" };
    }

    // 生成唯一文件路径
    const photoId = `photo_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const cloudPath = `memory_photos/${elderDeviceId}/${photoId}.${fileExtension}`;

    // 使用 getUploadMetadata 生成直传 URL
    try {
      console.log("尝试 getUploadMetadata, cloudPath:", cloudPath);
      const uploadMetadata = await app.getUploadMetadata({
        cloudPath: cloudPath,
        contentType: "image/jpeg",
      });

      console.log("getUploadMetadata 返回:", JSON.stringify(uploadMetadata));

      if (!uploadMetadata || !uploadMetadata.url) {
        return {
          success: false,
          message: "无法获取上传URL",
        };
      }

      return {
        success: true,
        data: {
          photoId,
          cloudPath,
          uploadUrl: uploadMetadata.url,
          authorization: uploadMetadata.authorization || "",
          token: uploadMetadata.token || "",
          fileId: uploadMetadata.fileId || "",
          cosFileId: uploadMetadata.cosFileId || null,
          expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
        },
      };
    } catch (metaErr) {
      console.log("getUploadMetadata 失败:", metaErr.message);
      return {
        success: false,
        message: `无法获取上传URL: ${metaErr.message}`,
      };
    }
  } catch (e) {
    console.error("生成上传凭证失败:", e);
    return { success: false, message: e.message };
  }
};
