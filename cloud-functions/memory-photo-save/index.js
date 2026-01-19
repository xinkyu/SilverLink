// memory-photo-save/index.js
// 保存照片元数据（图片已直传到 COS）
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

/**
 * 保存照片元数据
 * 图片已通过 COS 直传，此函数只保存元数据到数据库
 */
exports.main = async (event) => {
  try {
    const {
      elderDeviceId,
      familyDeviceId,
      photoId,
      cloudPath,
      fileId,
      description = "",
      aiDescription = "",
      takenDate = null,
      location = null,
      people = null,
      tags = null,
    } = getParams(event);

    if (!elderDeviceId || !familyDeviceId || !cloudPath) {
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

    // 获取文件访问 URL
    let imageUrl = fileId || cloudPath;
    try {
      const tempUrlRes = await app.getTempFileURL({
        fileList: [fileId || `cloud://${process.env.TCB_ENV}/${cloudPath}`],
      });
      if (tempUrlRes.fileList && tempUrlRes.fileList[0]) {
        imageUrl = tempUrlRes.fileList[0].tempFileURL || imageUrl;
      }
    } catch (e) {
      console.log("获取临时 URL 失败，使用 fileId:", e.message);
    }

    const now = new Date();
    const photoData = {
      elderDeviceId,
      familyDeviceId,
      photoId: photoId || `photo_${Date.now()}`,
      cloudPath,
      fileId: fileId || null,
      imageUrl,
      thumbnailUrl: null,
      description,
      aiDescription,
      takenDate,
      location,
      people,
      tags,
      createdAt: now,
    };

    const result = await db.collection("memory_photos").add(photoData);

    return {
      success: true,
      data: {
        id: result.id,
        elderDeviceId,
        familyDeviceId,
        imageUrl,
        cloudPath,
        description,
        aiDescription,
        takenDate,
        location,
        people,
        tags,
        createdAt: now.toISOString(),
      },
    };
  } catch (e) {
    console.error("保存照片元数据失败:", e);
    return { success: false, message: e.message };
  }
};
