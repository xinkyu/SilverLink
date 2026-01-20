const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
    env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();

/**
 * 家人端查询老人位置
 * 返回最近2小时内的位置历史
 */
exports.main = async (event) => {
    try {
        // 兼容 HTTP 请求 (event.body) 和 SDK 调用 (event)
        let params = event;

        // 1. 处理 GET 请求参数
        if (event.queryStringParameters) {
            params = { ...params, ...event.queryStringParameters };
        }

        // 2. 处理 POST 请求参数
        if (event.body) {
            try {
                const body = JSON.parse(event.body);
                params = { ...params, ...body };
            } catch (e) {
                console.warn("JSON解析失败", e);
            }
        }

        const { elderDeviceId, familyDeviceId } = params;

        if (!elderDeviceId) {
            return { success: false, message: "参数不完整" };
        }

        // 可选：验证家人是否有权限查看该老人的位置
        // 如果需要权限验证，可以查询 pairing_codes 表

        const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000);

        // 查询最近2小时内的位置记录，按时间倒序
        const { data } = await db
            .collection("elder_locations")
            .where({
                elderDeviceId,
                createdAt: db.command.gte(twoHoursAgo),
            })
            .orderBy("createdAt", "desc")
            .limit(50)
            .get();

        if (!data || data.length === 0) {
            return {
                success: true,
                data: {
                    latest: null,
                    history: [],
                },
            };
        }

        // 格式化返回数据
        const history = data.map((item) => ({
            id: item._id,
            latitude: item.latitude,
            longitude: item.longitude,
            accuracy: item.accuracy || 0,
            address: item.address || "",
            createdAt: item.createdAt
                ? item.createdAt.toISOString
                    ? item.createdAt.toISOString()
                    : new Date(item.createdAt).toISOString()
                : null,
        }));

        return {
            success: true,
            data: {
                latest: history[0] || null,
                history: history,
            },
        };
    } catch (e) {
        return { success: false, message: e.message };
    }
};
