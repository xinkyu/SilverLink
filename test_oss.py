import requests
import json

# 配置信息
API_KEY = "sk-6ab888a70a984244aa1241dc4109786c"  # 测试完后去阿里云重置

# 正确的 API 端点（根据阿里云官方文档）
URL = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization"

# 你的阿里云 OSS 文件链接
OSS_FILE_URL = "https://7369-silverlink-9gdqj1ne4d834dab-1396514174.tcb.qcloud.la/voice_cloning/3da38f0b39612e45/voice_1768893878016_xjo66l6il.m4a"

headers = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

# 正确的请求体格式（根据阿里云官方文档）
# 注意：action, target_model, prefix, url, language_hints 都在 input 中
data = {
    "model": "voice-enrollment",
    "input": {
        "action": "create_voice",
        "target_model": "cosyvoice-v3-plus",
        "prefix": "testvoice",
        "url": OSS_FILE_URL,
        "language_hints": ["zh"]
    }
}

try:
    print(f"正在提交任务...")
    print(f"URL: {URL}")
    print(f"Audio URL: {OSS_FILE_URL}")
    print(f"Request body: {json.dumps(data, indent=2, ensure_ascii=False)}")
    print()
    
    response = requests.post(URL, headers=headers, json=data)
    
    # 打印原始响应
    print(f"状态码: {response.status_code}")
    print("响应内容:")
    print(json.dumps(response.json(), indent=4, ensure_ascii=False))

except Exception as e:
    print(f"发生错误: {e}")