// memory-photo-upload/index.js
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
 * 上传记忆照片
 * 接收 Base64 图片和元数据，存入云数据库
 */
exports.main = async (event) => {
  try {
    const {
      elderDeviceId,
      familyDeviceId,
      imageBase64,
      description = "",
      aiDescription = "",
      takenDate = null,
      location = null,
      people = null,
      tags = null,
    } = getParams(event);

    if (!elderDeviceId || !familyDeviceId || !imageBase64) {
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

    // 上传图片到云存储（推荐方式）
    const photoId = `photo_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const base64Data = imageBase64.replace(
      /^data:image\/(png|jpg|jpeg);base64,/,
      "",
    );
    const uploadResult = await app.uploadFile({
      cloudPath: `memory_photos/${elderDeviceId}/${photoId}.jpg`,
      fileContent: Buffer.from(base64Data, "base64"),
    });

    let imageUrl = uploadResult.fileID;
    try {
      const tempUrlRes = await app.getTempFileURL({
        fileList: [uploadResult.fileID],
      });
      if (tempUrlRes.fileList && tempUrlRes.fileList[0]) {
        imageUrl = tempUrlRes.fileList[0].tempFileURL || uploadResult.fileID;
      }
    } catch (e) {
      // 忽略临时 URL 获取失败，保留 fileID
    }

    const now = new Date();
    const photoData = {
      elderDeviceId,
      familyDeviceId,
      // 云存储 URL / fileID
      imageUrl,
      fileId: uploadResult.fileID,
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
        imageUrl: photoData.imageUrl,
        thumbnailUrl: null,
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
    console.error("上传照片失败:", e);
    return { success: false, message: e.message };
  }
};
