"""Score the final decoded MP3 against spoken Telugu using independent CTC recognition."""
import json
import math
import re
import subprocess
import sys
import unicodedata
from pathlib import Path

import imageio_ffmpeg
import numpy as np
import onnxruntime as ort
import soundfile as sf
from scipy.signal import resample_poly
from scipy.signal.windows import hann

work, out = map(Path, sys.argv[1:])
opts = ort.SessionOptions()
opts.intra_op_num_threads = 2
opts.inter_op_num_threads = 1
session = ort.InferenceSession(str(work / "stt/indicconformer_int8.onnx"), opts)
vocab = json.loads((work / "stt/vocab.json").read_text())
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()

def mel(f):
    return np.where(f < 1000, f / (200 / 3), 15 + np.log(np.maximum(1000, f) / 1000) / (np.log(6.4) / 27))

def hz(m):
    return np.where(m < 15, m * 200 / 3, 1000 * np.exp((m - 15) * np.log(6.4) / 27))

edges = hz(np.linspace(0, mel(8000), 82))
freq = np.linspace(0, 8000, 257)
bank = np.maximum(0, np.minimum((freq[None, :] - edges[:-2, None]) / np.diff(edges)[:-1, None],
                                (edges[2:, None] - freq[None, :]) / np.diff(edges)[1:, None]))
bank *= (2 / (edges[2:] - edges[:-2]))[:, None]

def recognize(x):
    x = np.r_[x[0], x[1:] - .97*x[:-1]]
    windows = np.lib.stride_tricks.sliding_window_view(np.pad(x, 256, "reflect"), 512)[::160]
    power = abs(np.fft.rfft(windows * np.pad(hann(400, sym=False), 56)))**2
    features = np.log(np.maximum(1e-10, bank @ power.T))
    features -= features.mean(1, keepdims=True)
    features /= np.maximum(1e-5, features.std(1, keepdims=True, ddof=1))
    scores = session.run(None, {"audio_signal": features[None].astype("float32"),
                                "length": np.array([features.shape[1]], dtype="int64")})[0][0]
    ids = scores.argmax(-1)
    ids = ids[np.r_[True, ids[1:] != ids[:-1]]]
    return "".join(vocab[i] for i in ids if i < len(vocab)).replace("▁"," ").strip()

def words(text):
    text = unicodedata.normalize("NFC",text).replace("\u200c","").replace("\u200d","")
    return "".join(" " if unicodedata.category(c).startswith("P") else c for c in text).split()

def distance(a,b):
    row = list(range(len(b)+1))
    for i, word in enumerate(a,1):
        nxt = [i]
        for j,w in enumerate(b,1):
            nxt.append(min(row[j]+1,nxt[-1]+1,row[j-1]+(w!=word)))
        row = nxt
    return row[-1]

expected = words((out / "spoken.txt").read_text())
audit = {}
for wav in sorted(out.glob("*.wav")):
    mp3 = wav.with_suffix(".mp3")
    subprocess.run([ffmpeg,"-loglevel","error","-y","-i",str(wav),"-ar","44100","-ac","1",
                    "-c:a","libmp3lame","-b:a","96k",str(mp3)],check=True)
    pcm = np.frombuffer(subprocess.check_output([ffmpeg,"-loglevel","error","-i",str(mp3),
                        "-ar","16000","-ac","1","-f","f32le","-"]),dtype="<f4")
    if not np.isfinite(pcm).all() or len(pcm)<16000 or np.std(pcm)<.005:
        raise RuntimeError("Invalid narration " + wav.stem)
    transcript = recognize(pcm)
    if not transcript: raise RuntimeError("Empty transcript " + wav.stem)
    count = distance(expected,words(transcript))
    audit[wav.stem] = {"seconds":round(len(pcm)/16000,2),"transcript":transcript,
                       "asr_word_edits":count,"expected_words":len(expected),
                       "asr_word_error_rate":round(count/len(expected),3)}
(out / "recognition.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False))
print(json.dumps(audit,indent=2,ensure_ascii=False))
