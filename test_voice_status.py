import requests
import json

# 配置信息
API_KEY = "sk-6ab888a70a984244aa1241dc4109786c"

# API 端点
CUSTOMIZATION_URL = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization"

# 你的复刻音色ID
VOICE_ID = "cosyvoice-v3-plus-voice-ac11b52dd7104ba18596c74eb0b6c633"

headers = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

# 查询音色状态
print("========== 查询音色状态 ==========")
query_data = {
    "model": "voice-enrollment",
    "input": {
        "action": "query_voice",
        "voice_id": VOICE_ID
    }
}

try:
    response = requests.post(CUSTOMIZATION_URL, headers=headers, json=query_data)
    print(f"状态码: {response.status_code}")
    result = response.json()
    print(f"响应: {json.dumps(result, indent=2, ensure_ascii=False)}")
    
    status = result.get("output", {}).get("status")
    if status == "OK":
        print("\n✓ 音色状态: OK - 可以使用")
    elif status == "DEPLOYING":
        print("\n⏳ 音色状态: DEPLOYING - 请等待几分钟后再试")
    else:
        print(f"\n⚠ 音色状态: {status}")
except Exception as e:
    print(f"查询失败: {e}")
