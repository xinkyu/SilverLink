// memory-photo-search/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

/**
 * 搜索记忆照片
 * 根据自然语言查询匹配照片的描述、地点、人物、标签等
 */
exports.main = async (event) => {
  try {
    const { elderDeviceId, query, limit = 10 } = event;

    if (!elderDeviceId || !query) {
      return { success: false, message: "参数不完整" };
    }

    // 提取关键词（简单分词）
    const keywords = query
      .replace(/[，。？！、]/g, " ")
      .split(/\s+/)
      .filter((w) => w.length > 0);

    if (keywords.length === 0) {
      return { success: true, data: [] };
    }

    // 构建模糊查询条件
    // 在 description, aiDescription, location, people, tags 中搜索
    const orConditions = [];
    
    for (const keyword of keywords) {
      orConditions.push({ description: db.RegExp({ regexp: keyword, options: "i" }) });
      orConditions.push({ aiDescription: db.RegExp({ regexp: keyword, options: "i" }) });
      orConditions.push({ location: db.RegExp({ regexp: keyword, options: "i" }) });
      orConditions.push({ people: db.RegExp({ regexp: keyword, options: "i" }) });
      orConditions.push({ tags: db.RegExp({ regexp: keyword, options: "i" }) });
    }

    const { data } = await db
      .collection("memory_photos")
      .where(_.and([
        { elderDeviceId },
        _.or(orConditions)
      ]))
      .orderBy("createdAt", "desc")
      .limit(limit)
      .get();

    // 转换数据格式
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
    console.error("搜索照片失败:", e);
    return { success: false, message: e.message };
  }
};
