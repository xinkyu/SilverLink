const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
    env: cloud.SYMBOL_CURRENT_ENV,
});
const db = app.database();
const _ = db.command;

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
 * 查询紧急事件（家人端轮询调用）
 */
exports.main = async (event) => {
    try {
        const params = getParams(event);
        const { familyDeviceId, elderDeviceId, onlyUnresolved = true } = params;

        console.log("emergency-query 收到参数:", {
            familyDeviceId,
            elderDeviceId,
            onlyUnresolved,
        });

        if (!familyDeviceId) {
            return { success: false, message: "缺少 familyDeviceId 参数" };
        }

        // 获取该家人配对的所有老人设备ID
        let elderDeviceIds = [];
        if (elderDeviceId) {
            elderDeviceIds = [elderDeviceId];
        } else {
            const pairingResult = await db
                .collection("pairing_codes")
                .where({ familyDeviceId, status: "paired" })
                .get();
            if (pairingResult.data && pairingResult.data.length > 0) {
                elderDeviceIds = pairingResult.data
                    .map((p) => p.elderDeviceId)
                    .filter((id) => id);
            }
        }

        if (elderDeviceIds.length === 0) {
            return { success: true, data: [] };
        }

        // 查询紧急事件
        let query = db
            .collection("emergency_events")
            .where({ elderDeviceId: _.in(elderDeviceIds) });

        if (onlyUnresolved) {
            query = query.where({ resolved: false });
        }

        const result = await query
            .orderBy("timestamp", "desc")
            .limit(50)
            .get();

        const events = (result.data || []).map((item) => ({
            id: item._id,
            elderDeviceId: item.elderDeviceId,
            elderName: item.elderName || "",
            eventType: item.eventType,
            latitude: item.latitude,
            longitude: item.longitude,
            timestamp: item.timestamp,
            resolved: item.resolved || false,
            resolvedAt: item.resolvedAt,
            resolvedBy: item.resolvedBy,
        }));

        console.log(`查询到 ${events.length} 条紧急事件`);

        return { success: true, data: events };
    } catch (e) {
        console.error("emergency-query 错误:", e);
        return { success: false, message: e.message };
    }
};
