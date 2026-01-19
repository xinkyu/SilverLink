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
 * 标记紧急事件已处理（家人确认安全后调用）
 */
exports.main = async (event) => {
    try {
        const params = getParams(event);
        const { eventId, familyDeviceId } = params;

        console.log("emergency-resolve 收到参数:", {
            eventId,
            familyDeviceId,
        });

        if (!eventId) {
            return { success: false, message: "缺少 eventId 参数" };
        }

        if (!familyDeviceId) {
            return { success: false, message: "缺少 familyDeviceId 参数" };
        }

        // 更新事件状态为已处理
        await db.collection("emergency_events").doc(eventId).update({
            resolved: true,
            resolvedAt: Date.now(),
            resolvedBy: familyDeviceId,
        });

        console.log("紧急事件已标记为已处理:", eventId);

        return { success: true };
    } catch (e) {
        console.error("emergency-resolve 错误:", e);
        return { success: false, message: e.message };
    }
};
