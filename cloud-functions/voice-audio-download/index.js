const cloud = require("@cloudbase/node-sdk");

const app = cloud.init({
  env: cloud.SYMBOL_CURRENT_ENV,
});

/**
 * 声音复刻音频代理下载
 * 
 * 支持两种 URL 格式：
 * 1. 查询参数: /voice-audio-download?fileId=cloud://...
 * 2. 路径格式: /voice-audio-download?path=voice_cloning/familyId/filename.m4a
 * 
 * 路径格式更简洁，推荐用于第三方 API 调用（如阿里云声音复刻）
 */
exports.main = async (event, context) => {
  try {
    const queryParams = event.queryStringParameters || {};
    const { fileId: rawFileId, path: rawPath } = queryParams;
    
    const method =
      event.httpMethod ||
      (event.requestContext && event.requestContext.http
        ? event.requestContext.http.method
        : undefined);

    let fileId = null;

    // 优先使用 path 参数（更简洁的格式）
    if (rawPath) {
      // path 格式: voice_cloning/familyId/filename.m4a
      // 转换为 fileId 格式: cloud://env.bucket/voice_cloning/familyId/filename.m4a
      let decodedPath = rawPath;
      try {
        decodedPath = decodeURIComponent(rawPath);
      } catch (e) {
        // ignore
      }
      // 构造完整的 fileId
      // 注意: 需要知道 bucket 信息，这里从环境变量或硬编码获取
      const envId = process.env.TCB_ENV || "silverlink-9gdqj1ne4d834dab";
      const bucket = `7369-${envId}-1396514174`;
      fileId = `cloud://${envId}.${bucket}/${decodedPath}`;
      console.log("Using path-based fileId:", fileId);
    } else if (rawFileId) {
      // 使用传统的 fileId 参数
      try {
        fileId = decodeURIComponent(rawFileId);
      } catch (e) {
        fileId = rawFileId;
      }
      console.log("Using query-based fileId:", fileId);
    }

    if (!fileId) {
      return {
        statusCode: 400,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: "Missing fileId or path parameter" }),
      };
    }

    console.log("Downloading fileId:", fileId);

    // 下载文件
    const result = await app.downloadFile({
      fileID: fileId,
    });

    if (!result.fileContent) {
      return {
        statusCode: 404,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: "File not found" }),
      };
    }

    // 确定 Content-Type
    let contentType = "application/octet-stream";
    if (fileId.endsWith(".m4a")) contentType = "audio/mp4";
    else if (fileId.endsWith(".mp3")) contentType = "audio/mpeg";
    else if (fileId.endsWith(".wav")) contentType = "audio/wav";

    const contentLength = result.fileContent.length;

    if (method && method.toUpperCase() === "HEAD") {
      return {
        statusCode: 200,
        headers: {
          "Content-Type": contentType,
          "Content-Length": String(contentLength),
          "Cache-Control": "public, max-age=3600",
          "Accept-Ranges": "bytes",
          "Content-Disposition": "inline",
        },
        body: "",
      };
    }

    return {
      statusCode: 200,
      headers: {
        "Content-Type": contentType,
        "Content-Length": String(contentLength),
        "Cache-Control": "public, max-age=3600",
        "Accept-Ranges": "bytes",
        "Content-Disposition": "inline",
      },
      isBase64Encoded: true,
      body: result.fileContent.toString("base64"),
    };
  } catch (error) {
    console.error("Download error:", error);
    return {
      statusCode: 500,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message: error.message }),
    };
  }
};
