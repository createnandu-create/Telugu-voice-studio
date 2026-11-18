"""Fit timing from Telugu F/M index parquet files of sulabhkatiyar/indic-tts-mini-24k
at 8fe16c93456540d567f4ae952adb18ab173912b3 (AI4Bharat/Rasa, CC BY 4.0).
Usage: python fit-conversation-timing.py te_F.parquet te_M.parquet
"""
import sys
import numpy as np
import pyarrow.parquet as parquet
from scipy.optimize import least_squares


def features(text):
    letters = sum(0xC05 <= ord(c) <= 0xC14 or 0xC15 <= ord(c) <= 0xC39 for c in text)
    clusters = text.count("్")
    words = sum(any(0xC05 <= ord(c) <= 0xC39 for c in word) for word in text.split())
    return [1, letters - clusters, words, clusters]


rows = [row for path in sys.argv[1:] for row in parquet.read_table(path).to_pylist()
        if row["style_class"] == "conv"]
def data(split):
    selected = [r for r in rows if r["split"] == split and features(r["text"])[1] >= 2]
    return np.array([features(r["text"]) for r in selected]), np.array([r["duration_s"] for r in selected])


x, y = data("train")
coef = least_squares(lambda b: x @ b - y, [.25, .1, .1, .04],
                     bounds=(0, np.inf), loss="huber", f_scale=.5).x
print("train", len(y), "coefficients", coef)
for split in ("val", "test"):
    a, b = data(split)
    if len(b):
        print(split, len(b), "median absolute error in seconds", np.median(abs(a @ coef - b)))
