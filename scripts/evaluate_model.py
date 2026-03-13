import numpy as np
import onnxruntime as ort
import csv
import os
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    data_path = os.path.join(project_root, "acc_data.csv")
    labels_path = os.path.join(project_root, "acc_labels.csv")
    model_path = os.path.join(project_root, "app", "src", "main", "assets", "fall_detection_model.onnx")
    
    print(f"Loading data from {data_path}...")
    
    # Load labels
    labels = []
    with open(labels_path, 'r') as f:
        reader = csv.reader(f)
        for row in reader:
            # 1st column is activity class (1-17)
            activity = int(row[0])
            # Classes 1-9 are ADLs (0), 10-17 are Falls (1)
            label = 1 if activity >= 10 else 0
            labels.append(label)
    
    labels = np.array(labels, dtype=np.float32)
    
    # Load data
    features = []
    with open(data_path, 'r') as f:
        reader = csv.reader(f)
        for row in reader:
            row_data = np.array(row, dtype=np.float32)
            # data is 151 X, 151 Y, 151 Z
            x = row_data[0:151]
            y = row_data[151:302]
            z = row_data[302:453]
            
            # Center crop 100 steps
            start = 25
            end = 125
            x = x[start:end]
            y = y[start:end]
            z = z[start:end]
            
            mag = np.sqrt(x**2 + y**2 + z**2)
            
            # Shape (4, 100)
            sample = np.stack([x, y, z, mag], axis=0)
            features.append(sample)
            
    features = np.array(features, dtype=np.float32)
    
    print(f"Features shape: {features.shape}")
    print(f"Labels shape: {labels.shape}")
    print(f"Positive samples: {np.sum(labels):.0f}, Negative samples: {len(labels) - np.sum(labels):.0f}")
    
    print(f"\nLoading model from {model_path}...")
    session = ort.InferenceSession(model_path)
    
    input_name = session.get_inputs()[0].name
    
    print("Running inference...")
    
    # Run in batches
    batch_size = 64
    preds = []
    for i in range(0, len(features), batch_size):
        batch_features = features[i:i+batch_size]
        outputs = session.run(None, {input_name: batch_features})
        batch_preds = outputs[0].flatten()
        preds.extend(batch_preds)
        
    preds = np.array(preds)
    pred_labels = (preds > 0.5).astype(np.float32)
    
    # Metrics
    acc = accuracy_score(labels, pred_labels)
    prec = precision_score(labels, pred_labels, zero_division=0)
    rec = recall_score(labels, pred_labels, zero_division=0)
    f1 = f1_score(labels, pred_labels, zero_division=0)
    cm = confusion_matrix(labels, pred_labels)
    
    print("\n================ Evaluation Results ================")
    print(f"Accuracy:  {acc:.4f} ({acc*100:.2f}%)")
    print(f"Precision: {prec:.4f} ({prec*100:.2f}%)")
    print(f"Recall:    {rec:.4f} ({rec*100:.2f}%)")
    print(f"F1-Score:  {f1:.4f} ({f1*100:.2f}%)")
    print("\nConfusion Matrix:")
    print("                 Predicted ADL(0) | Predicted Fall(1)")
    print(f"Actual ADL(0)  | {cm[0,0]:<16} | {cm[0,1]:<17}")
    print(f"Actual Fall(1) | {cm[1,0]:<16} | {cm[1,1]:<17}")
    print("====================================================")
    
if __name__ == "__main__":
    main()
