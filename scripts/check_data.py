import csv

def check_csv(filepath):
    num_rows = 0
    num_cols = 0
    with open(filepath, 'r') as f:
        reader = csv.reader(f)
        for i, row in enumerate(reader):
            if i == 0:
                num_cols = len(row)
            num_rows += 1
    return num_rows, num_cols

print("acc_data_fps.csv:", check_csv(r'c:\Users\HP\Desktop\SilverLink\acc_data_fps.csv'))
print("acc_data.csv:", check_csv(r'c:\Users\HP\Desktop\SilverLink\acc_data.csv'))

labels = []
with open(r'c:\Users\HP\Desktop\SilverLink\acc_labels.csv', 'r') as f:
    reader = csv.reader(f)
    for row in reader:
        labels.append(int(row[-1]))

print("acc_labels.csv rows:", len(labels))
unique_labels = set(labels)
print("Unique label values:", unique_labels)
print("Max label value:", max(unique_labels))
