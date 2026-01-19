// memory-photo-list/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

/**
 * 获取记忆照片列表
 * 支持分页和增量同步
 */
exports.main = async (event) => {
  try {
    const {
      elderDeviceId,
      familyDeviceId = null,
      page = 1,
      pageSize = 20,
      sinceTimestamp = null,
    } = event;

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

    // 转换数据格式（不返回完整 Base64 以节省带宽）
    const result = data.map((item) => ({
      id: item.id_ || item._id,
      elderDeviceId: item.elderDeviceId,
      familyDeviceId: item.familyDeviceId,
      imageUrl: item.imageUrl,
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
    }));

    return { success: true, data: result };
  } catch (e) {
    console.error("获取照片列表失败:", e);
    return { success: false, message: e.message };
  }
};
