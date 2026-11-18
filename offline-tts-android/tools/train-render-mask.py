"""Fit the on-device spectral gain table with public Telugu studio speech and seeded synthetic noise.
Input directory: te_{F,M}.parquet and first 50 MiB of te_{F,M}_00000.tar as te_{F,M}.tar
from sulabhkatiyar/indic-tts-mini-24k@8fe16c93456540d567f4ae952adb18ab173912b3
(AI4Bharat/Rasa, CC BY 4.0). No user recordings or speaker embeddings enter training.
"""
import io
import sys
from pathlib import Path
from collections import Counter
import numpy as np
import pyarrow.parquet as pq
import soundfile as sf
from scipy.ndimage import uniform_filter1d
from scipy.signal import lfilter

root = Path(sys.argv[1])
rng = np.random.default_rng(843)
n = 512
freq = np.fft.rfftfreq(n, 1/24000)
band = np.searchsorted([700, 1800, 3500, 6000], freq, side="right")
window = np.sin(np.pi * (np.arange(n) + .5) / n)
def frames(x):
    chunks = np.lib.stride_tricks.sliding_window_view(x, n)[::256]
    return abs(np.fft.rfft(chunks * window)) ** 2

def index(p):
    means = np.stack([p[:,band==b].mean(1) for b in range(5)], axis=1)
    # Compare each bin to its own band and to average broadband energy.
    base = (means[:,0:1]*.35 + means[:,1:2]*.5 + means[:,2:3]*.15)
    level = np.clip(np.floor((10*np.log10((p+1e-12)/(base+1e-12))+45)/8),0,7)
    smooth = uniform_filter1d(p, 11, axis=1, mode="nearest")
    crest = np.clip(np.floor((10*np.log10((p+1e-12)/(smooth+1e-12))+12)/4),0,5)
    fric = (means[:,3:4]+means[:,4:5] > .20*base)
    return (((fric.astype(int)*5+band)*8+level.astype(int))*6+crest.astype(int))

def example(x, clean):
    noise = rng.normal(size=x.size)
    kind = rng.integers(4)
    if kind == 0: noise = lfilter([1,-.86],[1],noise)
    if kind == 1: noise = lfilter([1],[1,-.86],noise)
    if kind == 2: noise *= .35+.65*np.sin(np.arange(x.size)*np.pi*rng.uniform(15,70)/24000)**2
    if kind == 3:
        noise += np.sin(np.arange(x.size)*2*np.pi*rng.uniform(1600,6900)/24000)*.65
    noise *= np.sqrt(np.mean(x*x)/(np.mean(noise*noise)+1e-12))*10**(-rng.uniform(8,32)/20)
    if clean: noise *= 0
    c,m,e = frames(x),frames(x+noise),frames(noise)
    target = np.sqrt(c/(c+e+1e-12))
    return index(m),target,c,e

sums=np.full(480, .0001)
weights=np.full(480, .0001)
held=[]
count=Counter()
for sex in ("F","M"):
    key="te_"+sex
    archive=root/(key+".tar")
    rows=pq.read_table(root/(key+".parquet")).to_pylist()
    with archive.open("rb") as f:
        for row in rows:
            offset,size=row["member_offset"],row["member_size"]
            if not row["shard"].endswith("00000.tar") or offset+size>min(50*1024**2,archive.stat().st_size): continue
            split=row["split"]
            if split not in ("train","eval") or (split=="eval" and count[sex,split]>=20): continue
            f.seek(offset)
            x,sr=sf.read(io.BytesIO(f.read(size)))
            assert sr==24000
            ix,y,c,e=example(x[:sr*6], clean=split=="train" and rng.random()<.24)
            if split=="train":
                # Solve the conditional minimum reconstruction-error gain for each cell.
                scale = 1/max(c.sum()*.0025,e.sum(),1e-12)
                sums+=np.bincount(ix.ravel(),weights=(scale*c).ravel(),minlength=480)
                weights+=np.bincount(ix.ravel(),weights=(scale*(c+e)).ravel(),minlength=480)
            else: held.append((ix,c,e))
            count[sex,split]+=1
table=np.rint(100*sums/weights).astype(int)
def assessment(ix,c,e):
    floor=np.array([.84,.76,.58,.43,.36])[band]
    gain=np.maximum(floor,table[ix]/100)
    return 10*np.log10(e.sum()/(((1-gain)**2*c+gain**2*e).sum()+1e-20))
print("Clips",count,"held-out median spectral SNR improvement (dB)",np.median([assessment(*z) for z in held]),file=sys.stderr)
for row in table.reshape(-1,24): print("      "+", ".join(map(str,row))+",")
