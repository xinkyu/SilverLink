// voice-audio-upload/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});

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
 * 上传声音复刻音频文件
 * 将音频上传到云存储并返回公网可访问的URL
 *
 * 请求参数:
 * - familyDeviceId: 家人设备ID
 * - audioBase64: Base64编码的音频数据
 * - format: 音频格式 (wav/mp3/m4a)
 *
 * 返回:
 * - success: 是否成功
 * - url: 公网可访问的音频URL
 * - fileId: 云存储文件ID
 */
exports.main = async (event) => {
  try {
    const { familyDeviceId, audioBase64, format = "wav" } = getParams(event);

    if (!familyDeviceId || !audioBase64) {
      return {
        success: false,
        message: "参数不完整，需要 familyDeviceId 和 audioBase64",
      };
    }

    // 生成唯一文件名
    const timestamp = Date.now();
    const randomStr = Math.random().toString(36).substr(2, 9);
    const fileName = `voice_${timestamp}_${randomStr}.${format}`;
    const cloudPath = `voice_cloning/${familyDeviceId}/${fileName}`;

    // 移除可能的 data URL 前缀
    const base64Data = audioBase64.replace(
      /^data:audio\/(wav|mp3|m4a|mpeg|x-wav);base64,/i,
      "",
    );

    // 上传到云存储
    const uploadResult = await app.uploadFile({
      cloudPath: cloudPath,
      fileContent: Buffer.from(base64Data, "base64"),
    });

    if (!uploadResult.fileID) {
      return {
        success: false,
        message: "上传失败",
      };
    }

    // 获取临时访问URL（有效期较长）
    const tempUrlRes = await app.getTempFileURL({
      fileList: [uploadResult.fileID],
    });

    let publicUrl = null;
    if (
      tempUrlRes.fileList &&
      tempUrlRes.fileList[0] &&
      tempUrlRes.fileList[0].tempFileURL
    ) {
      publicUrl = tempUrlRes.fileList[0].tempFileURL;
    }

    if (!publicUrl) {
      return {
        success: false,
        message: "无法获取公网访问URL",
        fileId: uploadResult.fileID,
      };
    }

    console.log(
      `Voice audio uploaded: ${cloudPath}, URL: ${publicUrl.substring(0, 50)}...`,
    );

    return {
      success: true,
      data: {
        url: publicUrl,
        fileId: uploadResult.fileID,
        cloudPath: cloudPath,
      }
    };
  } catch (error) {
    console.error("Voice audio upload error:", error);
    return {
      success: false,
      message: error.message || "上传失败",
    };
  }
};
