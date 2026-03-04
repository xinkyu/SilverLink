import onnx
import onnxruntime.quantization.quant_utils as quant_utils
from onnxruntime.quantization import quantize_dynamic, QuantType

# 跳过 shape inference
def patched_load(model_path):
    return onnx.load(str(model_path))

quant_utils.load_model_with_shape_infer = patched_load

quantize_dynamic(
    r"E:\Google download\onnx\model.onnx",
    r"E:\Google download\onnx\model_quantized.onnx",
    weight_type=QuantType.QInt8,
    extra_options={"DefaultTensorType": 1}  # 1 = FLOAT
)
print("✅ 量化完成")