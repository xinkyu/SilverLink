// cognitive-log/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

/**
 * 记录认知评估结果
 */
exports.main = async (event) => {
  try {
    const body =
      typeof event?.body === "string" ? JSON.parse(event.body) : event?.body;
    const {
      elderDeviceId,
      photoId,
      questionType,
      expectedAnswer,
      actualAnswer,
      isCorrect,
      responseTimeMs,
      confidence = 0,
    } = body || event;

    if (!elderDeviceId || !photoId || !questionType) {
      return { success: false, message: "参数不完整" };
    }

    const now = new Date();

    await db.collection("cognitive_logs").add({
      elderDeviceId,
      photoId,
      questionType,
      expectedAnswer: expectedAnswer || "",
      actualAnswer: actualAnswer || "",
      isCorrect: !!isCorrect,
      responseTimeMs: responseTimeMs || 0,
      confidence,
      createdAt: now,
    });

    return { success: true };
  } catch (e) {
    console.error("记录认知结果失败:", e);
    return { success: false, message: e.message };
  }
};
