// cognitive-report/index.js
const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

/**
 * 获取认知评估报告
 * 统计指定天数内的认知测试结果
 * 支持家人端（需要配对验证）和长辈端（直接查询自己的数据）
 */
exports.main = async (event) => {
  try {
    const body =
      typeof event?.body === "string" ? JSON.parse(event.body) : event?.body;
    const { elderDeviceId, familyDeviceId, days = 7 } = body || event;

    if (!elderDeviceId) {
      return { success: false, message: "参数不完整：缺少elderDeviceId" };
    }

    // 如果提供了 familyDeviceId，则需要验证配对关系（家人端调用）
    // 如果没有提供 familyDeviceId，则认为是长辈端直接查询自己的数据
    if (familyDeviceId) {
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
    }

    // 计算时间范围
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - days);

    // 查询认知记录
    const { data: logs } = await db
      .collection("cognitive_logs")
      .where({
        elderDeviceId,
        createdAt: _.gte(startDate),
      })
      .get();

    if (!logs || logs.length === 0) {
      return {
        success: true,
        data: {
          totalQuestions: 0,
          correctAnswers: 0,
          correctRate: 0,
          averageResponseTimeMs: 0,
          trend: "stable",
          startDate: startDate.toISOString().split("T")[0],
          endDate: endDate.toISOString().split("T")[0],
        },
      };
    }

    // 统计数据
    const totalQuestions = logs.length;
    const correctAnswers = logs.filter((l) => l.isCorrect).length;
    const correctRate =
      totalQuestions > 0 ? correctAnswers / totalQuestions : 0;

    const totalResponseTime = logs.reduce(
      (sum, l) => sum + (l.responseTimeMs || 0),
      0,
    );
    const averageResponseTimeMs =
      totalQuestions > 0 ? Math.round(totalResponseTime / totalQuestions) : 0;

    // 计算趋势（对比前半段和后半段的正确率）
    const midIndex = Math.floor(logs.length / 2);
    let trend = "stable";

    if (logs.length >= 4) {
      const firstHalf = logs.slice(0, midIndex);
      const secondHalf = logs.slice(midIndex);

      const firstRate =
        firstHalf.filter((l) => l.isCorrect).length / firstHalf.length;
      const secondRate =
        secondHalf.filter((l) => l.isCorrect).length / secondHalf.length;

      if (secondRate - firstRate > 0.1) {
        trend = "improving";
      } else if (firstRate - secondRate > 0.1) {
        trend = "declining";
      }
    }

    return {
      success: true,
      data: {
        totalQuestions,
        correctAnswers,
        correctRate: Math.round(correctRate * 100) / 100,
        averageResponseTimeMs,
        trend,
        startDate: startDate.toISOString().split("T")[0],
        endDate: endDate.toISOString().split("T")[0],
      },
    };
  } catch (e) {
    console.error("获取认知报告失败:", e);
    return { success: false, message: e.message };
  }
};
