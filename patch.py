import sys
with open(r'c:\Users\HP\Desktop\SilverLink\scripts\train_fall_detection_model.py', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = lines[:24]
real_dataset_code = """
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
"""
new_lines.append(real_dataset_code)
new_lines.extend(lines[191:])

# Now let's fix the main part as well
for i, line in enumerate(new_lines):
    if "[1/4] 生成合成训练数据..." in line:
        new_lines[i] = new_lines[i].replace("生成合成训练数据...", "加载真实训练数据...")
    elif "X, y = generate_dataset(n_samples=4000)" in line:
        new_lines[i] = '    csv_path = os.path.join(project_root, "DatasetUniba.csv")\n    X, y = load_real_dataset(csv_path)\n'

with open(r'c:\Users\HP\Desktop\SilverLink\scripts\train_fall_detection_model.py', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
