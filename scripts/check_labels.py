import csv
import numpy as np

# Read labels
labels = []
with open(r'c:\Users\HP\Desktop\SilverLink\acc_labels.csv', 'r') as f:
    reader = csv.reader(f)
    for row in reader:
        labels.append(int(row[-1]))
labels = np.array(labels)

# Read few rows of data and compute max magnitude per label to identify falls
class_max_mag = {1: [], 2: [], 3: [], 4: [], 5: [], 6: []}

with open(r'c:\Users\HP\Desktop\SilverLink\acc_data.csv', 'r') as f:
    reader = csv.reader(f)
    for i, row in enumerate(reader):
        if i >= 11771: break
        label = labels[i]
        
        # 453 columns = 151 * 3
        data = np.array(row, dtype=float).reshape(151, 3)
        magnitudes = np.sqrt(np.sum(data**2, axis=1))
        max_mag = np.max(magnitudes)
        class_max_mag[label].append(max_mag)

for lbl in sorted(class_max_mag.keys()):
    mags = class_max_mag[lbl]
    if mags:
        print(f"Label {lbl}: count={len(mags)}, avg_max_mag={np.mean(mags):.2f}, max_max_mag={np.max(mags):.2f}")
