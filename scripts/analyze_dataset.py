import csv
from collections import defaultdict
import math

trials = defaultdict(lambda: {"min": float("inf"), "max": float("-inf"), "n": 0})

with open("DatasetUniba.csv", "r") as f:
    reader = csv.reader(f)
    for row in reader:
        key = (row[0], row[1])
        ts = int(row[2])
        info = trials[key]
        info["n"] += 1
        if ts < info["min"]:
            info["min"] = ts
        if ts > info["max"]:
            info["max"] = ts

print("=== Sample trials ===")
for k, v in sorted(trials.items(), key=lambda x: (x[0][1], int(x[0][0]))):
    if int(k[0]) <= 2:
        dur = (v["max"] - v["min"]) / 1000.0
        interval = (v["max"] - v["min"]) / max(1, v["n"] - 1) if v["n"] > 1 else 0
        hz = 1000.0 / interval if interval > 0 else 0
        print(f"  Trial {k[0]:>2} {k[1]:>15}: {v['n']:>5} samples, {dur:.1f}s, {interval:.1f}ms (~{hz:.0f}Hz)")

print("\n=== Summary per activity ===")
activity_stats = defaultdict(lambda: {"total_samples": 0, "trials": 0, "total_duration": 0})
for k, v in trials.items():
    act = k[1]
    s = activity_stats[act]
    s["total_samples"] += v["n"]
    s["trials"] += 1
    s["total_duration"] += (v["max"] - v["min"]) / 1000.0

for act, s in sorted(activity_stats.items()):
    avg_dur = s["total_duration"] / s["trials"]
    avg_samples = s["total_samples"] / s["trials"]
    print(f"  {act:>15}: {s['trials']} trials, avg {avg_dur:.1f}s/trial, avg {avg_samples:.0f} samples/trial")

x_vals, y_vals, z_vals = [], [], []
with open("DatasetUniba.csv", "r") as f:
    reader = csv.reader(f)
    for i, row in enumerate(reader):
        if i >= 10000:
            break
        x_vals.append(float(row[3]))
        y_vals.append(float(row[4]))
        z_vals.append(float(row[5]))

mags = [math.sqrt(x**2 + y**2 + z**2) for x, y, z in zip(x_vals, y_vals, z_vals)]
print(f"\n=== Value ranges (first 10000 rows) ===")
print(f"  X: [{min(x_vals):.2f}, {max(x_vals):.2f}]")
print(f"  Y: [{min(y_vals):.2f}, {max(y_vals):.2f}]")
print(f"  Z: [{min(z_vals):.2f}, {max(z_vals):.2f}]")
print(f"  Magnitude: [{min(mags):.2f}, {max(mags):.2f}]")
print(f"  Gravity approx = {sum(mags[:200])/200:.2f} (first 200 samples avg)")
