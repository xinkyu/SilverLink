"""
跌倒检测 1D-CNN 模型训练脚本

模型输入: [batch, 4, 100]  (4通道: x, y, z, magnitude; 100时间步: 2秒@50Hz)
模型输出: [batch, 1]       (跌倒概率: 0.0 - 1.0)
"""

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset
import os
import random
import matplotlib.pyplot as plt


SAMPLE_RATE = 50       # 50 Hz
WINDOW_SIZE = 100      # 2秒 = 100 个时间步
GRAVITY = 9.81



import csv
from collections import defaultdict

def load_real_dataset(csv_path):
    print(f"从 {csv_path} 加载真实数据集...")
    
    trials = defaultdict(list)
    with open(csv_path, 'r') as f:
        reader = csv.reader(f)
        for row in reader:
            if not row or len(row) < 6: continue
            subj, act = row[0], row[1]
            try:
                t = float(row[2])
                x = float(row[3])
                y = float(row[4])
                z = float(row[5])
            except ValueError:
                continue
            trials[(subj, act)].append((t, x, y, z))
            
    X_list = []
    y_list = []
    
    fall_acts = {"BackwardFall", "ForwardFall", "LeftFall", "RightFall"}
    
    WINDOW_MS = 2000
    STEP_MS = 500  # 0.5秒滑动步长，增加样本量
    
    for (subj, act), data in trials.items():
        data.sort(key=lambda d: d[0])
        times = np.array([d[0] for d in data])
        xs = np.array([d[1] for d in data])
        ys = np.array([d[2] for d in data])
        zs = np.array([d[3] for d in data])
        
        label = 1.0 if act in fall_acts else 0.0
        
        start_t = times[0]
        end_t = times[-1]
        
        curr_t = start_t
        while curr_t + WINDOW_MS <= end_t:
            target_times = np.linspace(curr_t, curr_t + WINDOW_MS, WINDOW_SIZE)
            
            resampled_x = np.interp(target_times, times, xs)
            resampled_y = np.interp(target_times, times, ys)
            resampled_z = np.interp(target_times, times, zs)
            magnitude = np.sqrt(resampled_x**2 + resampled_y**2 + resampled_z**2)
            
            sample = np.stack([resampled_x, resampled_y, resampled_z, magnitude], axis=0).astype(np.float32)
            X_list.append(sample)
            y_list.append(label)
            
            curr_t += STEP_MS
            
    X = np.array(X_list)
    y = np.array(y_list, dtype=np.float32)
    
    indices = np.arange(len(X))
    np.random.shuffle(indices)
    
    return X[indices], y[indices]

# ============================================================
# 2. 1D-CNN 模型定义
# ============================================================

class FallDetectionCNN(nn.Module):
    """
    轻量级 1D-CNN 跌倒检测模型

    输入: [batch, 4, 100]  (4通道: x, y, z, magnitude)
    输出: [batch, 1]       (跌倒概率)
    """

    def __init__(self):
        super(FallDetectionCNN, self).__init__()

        self.features = nn.Sequential(
            # Conv1: 4 -> 32 channels
            nn.Conv1d(4, 32, kernel_size=5, padding=2),
            nn.ReLU(),
            nn.MaxPool1d(kernel_size=2),             # 100 -> 50

            # Conv2: 32 -> 64 channels
            nn.Conv1d(32, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.MaxPool1d(kernel_size=2),             # 50 -> 25

            # Conv3: 64 -> 32 channels
            nn.Conv1d(64, 32, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.AdaptiveAvgPool1d(1),                 # 25 -> 1 (Global Average Pooling)
        )

        self.classifier = nn.Sequential(
            nn.Linear(32, 1),
            nn.Sigmoid()
        )

    def forward(self, x):
        x = self.features(x)
        x = x.squeeze(-1)  # [batch, 32, 1] -> [batch, 32]
        x = self.classifier(x)
        return x


# ============================================================
# 3. 训练流程
# ============================================================

def train_model(X_train, y_train, X_val, y_val, epochs=50, batch_size=64, lr=0.001):
    """训练模型"""
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"使用设备: {device}")

    model = FallDetectionCNN().to(device)
    criterion = nn.BCELoss()
    optimizer = optim.Adam(model.parameters(), lr=lr)
    scheduler = optim.lr_scheduler.ReduceLROnPlateau(optimizer, patience=5, factor=0.5)

    train_dataset = TensorDataset(
        torch.FloatTensor(X_train),
        torch.FloatTensor(y_train).unsqueeze(1)
    )
    val_dataset = TensorDataset(
        torch.FloatTensor(X_val),
        torch.FloatTensor(y_val).unsqueeze(1)
    )

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    best_val_acc = 0
    best_model_state = None

    history = {
        'train_loss': [],
        'val_loss': [],
        'train_acc': [],
        'val_acc': []
    }

    for epoch in range(epochs):
        # 训练
        model.train()
        train_loss = 0
        correct = 0
        total = 0

        for inputs, targets in train_loader:
            inputs, targets = inputs.to(device), targets.to(device)

            optimizer.zero_grad()
            outputs = model(inputs)
            loss = criterion(outputs, targets)
            loss.backward()
            optimizer.step()

            train_loss += loss.item()
            predicted = (outputs > 0.5).float()
            correct += (predicted == targets).sum().item()
            total += targets.size(0)

        train_acc = correct / total

        # 验证
        model.eval()
        val_loss = 0
        val_correct = 0
        val_total = 0

        with torch.no_grad():
            for inputs, targets in val_loader:
                inputs, targets = inputs.to(device), targets.to(device)
                outputs = model(inputs)
                loss = criterion(outputs, targets)

                val_loss += loss.item()
                predicted = (outputs > 0.5).float()
                val_correct += (predicted == targets).sum().item()
                val_total += targets.size(0)

        val_acc = val_correct / val_total
        scheduler.step(val_loss)

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            best_model_state = model.state_dict().copy()

        train_loss_avg = train_loss/len(train_loader)
        val_loss_avg = val_loss/len(val_loader)
        
        history['train_loss'].append(train_loss_avg)
        history['val_loss'].append(val_loss_avg)
        history['train_acc'].append(train_acc)
        history['val_acc'].append(val_acc)

        if (epoch + 1) % 10 == 0:
            print(f"Epoch {epoch+1}/{epochs} - "
                  f"Train Loss: {train_loss_avg:.4f}, "
                  f"Train Acc: {train_acc:.4f}, "
                  f"Val Loss: {val_loss_avg:.4f}, "
                  f"Val Acc: {val_acc:.4f}")

    # 加载最佳模型
    model.load_state_dict(best_model_state)
    print(f"\n最佳验证准确率: {best_val_acc:.4f}")
    return model, history


# ============================================================
# 4. ONNX 导出
# ============================================================

def export_onnx(model, output_path):
    """导出为 ONNX 格式（自动修复 PyTorch 2.x 外部数据分离 BUG）"""
    import onnx
    from onnx.external_data_helper import convert_model_to_external_data

    model.eval()
    dummy_input = torch.randn(1, 4, WINDOW_SIZE)

    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch_size"},
            "output": {0: "batch_size"},
        },
        opset_version=11,
        do_constant_folding=True,
    )

    # ========== 关键修复 ==========
    # PyTorch 2.x 的 Dynamo 导出器会把权重分离到 .onnx.data 文件中
    # Android 的 ONNX Runtime 无法在 assets 中读取分离的外部数据文件
    # 所以我们需要把权重全部内联回主 .onnx 文件
    data_file = output_path + ".data"
    if os.path.exists(data_file):
        print(f"  检测到外部数据文件 {data_file}，正在内联权重...")
        # 加载模型（带外部数据）
        onnx_model = onnx.load(output_path, load_external_data=True)
        # 清除所有外部数据引用，把权重写回 tensor 本体
        for tensor in onnx_model.graph.initializer:
            if tensor.data_location == onnx.TensorProto.EXTERNAL:
                tensor.data_location = onnx.TensorProto.DEFAULT
                # 清除外部数据的元信息
                del tensor.external_data[:]
        # 重新保存为单一文件
        onnx.save(onnx_model, output_path)
        # 删除多余的 .data 文件
        os.remove(data_file)
        print(f"  权重已内联，外部数据文件已删除")

    file_size = os.path.getsize(output_path) / 1024
    print(f"ONNX 模型导出成功: {output_path} ({file_size:.1f} KB)")


# ============================================================
# 主程序
# ============================================================

if __name__ == "__main__":
    random.seed(42)
    np.random.seed(42)
    torch.manual_seed(42)

    print("=" * 60)
    print("跌倒检测 1D-CNN 模型训练")
    print("=" * 60)

    # 1. 生成数据
    print("\n[1/4] 加载真实训练数据...")
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    csv_path = os.path.join(project_root, "DatasetUniba.csv")
    X, y = load_real_dataset(csv_path)
    print(f"数据形状: X={X.shape}, y={y.shape}")
    print(f"正样本(跌倒): {int(y.sum())}, 负样本: {int(len(y) - y.sum())}")

    # 2. 划分训练/验证集
    split = int(len(X) * 0.8)
    X_train, X_val = X[:split], X[split:]
    y_train, y_val = y[:split], y[split:]

    # 3. 训练
    print("\n[2/4] 训练模型...")
    model, history = train_model(X_train, y_train, X_val, y_val, epochs=50, batch_size=64)

    # 绘制并保存训练历史图表
    plt.figure(figsize=(10, 4))
    
    plt.subplot(1, 2, 1)
    plt.plot(history['train_loss'], label='Train Loss')
    plt.plot(history['val_loss'], label='Val Loss')
    plt.title('Loss History')
    plt.xlabel('Epoch')
    plt.ylabel('Loss')
    plt.legend()
    
    plt.subplot(1, 2, 2)
    plt.plot(history['train_acc'], label='Train Acc')
    plt.plot(history['val_acc'], label='Val Acc')
    plt.title('Accuracy History')
    plt.xlabel('Epoch')
    plt.ylabel('Accuracy')
    plt.legend()
    
    plt.tight_layout()
    plot_path = os.path.join(project_root, "training_history.png")
    plt.savefig(plot_path)
    print(f"训练历史图表已保存至: {plot_path}")

    # 4. 导出 ONNX
    print("\n[3/4] 导出 ONNX 模型...")
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)

    # 导出到 app/src/main/assets/
    app_assets = os.path.join(project_root, "app", "src", "main", "assets")
    os.makedirs(app_assets, exist_ok=True)
    app_model_path = os.path.join(app_assets, "fall_detection_model.onnx")
    export_onnx(model, app_model_path)

    # 导出到 wear/src/main/assets/
    wear_assets = os.path.join(project_root, "wear", "src", "main", "assets")
    os.makedirs(wear_assets, exist_ok=True)
    wear_model_path = os.path.join(wear_assets, "fall_detection_model.onnx")
    export_onnx(model, wear_model_path)

    print("\n[4/4] 完成!")
    print(f"App 模型: {app_model_path}")
    print(f"Wear 模型: {wear_model_path}")
    print("=" * 60)
