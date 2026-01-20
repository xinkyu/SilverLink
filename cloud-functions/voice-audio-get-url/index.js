const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});

exports.main = async (event, context) => {
  try {
    const { fileId } = event.body ? JSON.parse(event.body) : event;

    if (!fileId) {
      return { success: false, message: "缺少 fileId" };
    }

    // 获取临时访问URL（有效期默认较长，足以支持后续下载）
    const tempUrlRes = await app.getTempFileURL({
      fileList: [fileId],
    });

    let publicUrl = null;
    if (
      tempUrlRes.fileList &&
      tempUrlRes.fileList[0] &&
      tempUrlRes.fileList[0].tempFileURL
    ) {
      publicUrl = tempUrlRes.fileList[0].tempFileURL;
    }

    if (!publicUrl) {
      return { success: false, message: "无法获取访问URL" };
    }

    return {
      success: true,
      data: {
        url: publicUrl,
        fileId: fileId,
      },
    };
  } catch (error) {
    console.error("Get voice URL error:", error);
    return {
      success: false,
      message: error.message || "获取URL失败",
    };
  }
};
