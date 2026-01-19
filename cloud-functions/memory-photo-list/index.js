// memory-photo-list/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

/**
 * 解析请求参数（兼容 HTTP 和直接调用）
 */
function getParams(event) {
  if (!event) return {};
  
  // HTTP 触发器模式
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
  
  // Query 参数模式
  if (event.queryStringParameters) {
    return event.queryStringParameters;
  }
  
  // 直接调用模式
  return event;
}

/**
 * 获取记忆照片列表
 * 支持分页和增量同步
 */
exports.main = async (event) => {
  try {
    const params = getParams(event);
    const {
      elderDeviceId,
      familyDeviceId = null,
      page = 1,
      pageSize = 20,
      sinceTimestamp = null,
    } = params;

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整" };
    }

    // 构建查询条件
    const query = { elderDeviceId };
    
    // 如果有时间戳，只返回该时间戳之后的照片（用于增量同步）
    if (sinceTimestamp) {
      query.createdAt = _.gt(new Date(sinceTimestamp));
    }

    // 分页查询
    const skip = (page - 1) * pageSize;
    
    const { data } = await db
      .collection("memory_photos")
      .where(query)
      .orderBy("createdAt", "desc")
      .skip(skip)
      .limit(pageSize)
      .get();
    // 收集需要刷新 URL 的 fileId
    const fileIds = data
      .filter((item) => item.fileId)
      .map((item) => item.fileId);
    
    // 批量获取新的临时 URL
    let urlMap = {};
    if (fileIds.length > 0) {
      try {
        const tempUrlRes = await app.getTempFileURL({ fileList: fileIds });
        if (tempUrlRes.fileList) {
          tempUrlRes.fileList.forEach((file) => {
            if (file.tempFileURL) {
              urlMap[file.fileID] = file.tempFileURL;
            }
          });
        }
      } catch (e) {
        console.log("批量获取临时 URL 失败:", e.message);
      }
    }

    // 转换数据格式，使用刷新后的 URL
    const result = data.map((item) => {
      // 优先使用刷新后的临时 URL
      let imageUrl = item.imageUrl;
      if (item.fileId && urlMap[item.fileId]) {
        imageUrl = urlMap[item.fileId];
      }
      
      return {
        id: item.id_ || item._id,
        elderDeviceId: item.elderDeviceId,
        familyDeviceId: item.familyDeviceId,
        imageUrl: imageUrl,
        thumbnailUrl: item.thumbnailUrl,
        description: item.description || "",
        aiDescription: item.aiDescription || "",
        takenDate: item.takenDate,
        location: item.location,
        people: item.people,
        tags: item.tags,
        createdAt: item.createdAt instanceof Date 
          ? item.createdAt.toISOString() 
          : item.createdAt,
      };
    });

    console.log(`返回 ${result.length} 张照片`);
    return { success: true, data: result };
  } catch (e) {
    console.error("获取照片列表失败:", e);
    return { success: false, message: e.message };
  }
};
