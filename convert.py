from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch, os

model_path = r"E:\Google download"
output_path = r"E:\Google download\onnx\model.onnx"

tokenizer = AutoTokenizer.from_pretrained(model_path)
model = AutoModelForSequenceClassification.from_pretrained(
    model_path,
    use_safetensors=False  # 使用 pytorch_model.bin
)
model.eval()

dummy = tokenizer("I am so happy!", return_tensors="pt", padding=True, truncation=True, max_length=128)

os.makedirs(r"E:\Google download\onnx", exist_ok=True)

torch.onnx.export(
    model,
    (dummy["input_ids"], dummy["attention_mask"]),
    output_path,
    input_names=["input_ids", "attention_mask"],
    output_names=["logits"],
    dynamic_axes={
        "input_ids":      {0: "batch", 1: "seq"},
        "attention_mask": {0: "batch", 1: "seq"},
    },
    opset_version=14,
    do_constant_folding=True,
)

print(f"✅ 导出成功：{output_path}")
tokenizer.save_pretrained(r"E:\Google download\onnx")
print("✅ tokenizer 已保存")