"""Compare actual Android encoder output with the lossless pre-export master."""
import json
from pathlib import Path
import subprocess
import sys
import wave
import imageio_ffmpeg
import numpy as np

root = Path(sys.argv[1])
results = {}
for p in sorted(root.glob("*_Clean.mp3")):
    with wave.open(str(p.with_suffix(".wav"))) as w:
        rate = w.getframerate()
        ref = np.frombuffer(w.readframes(w.getnframes()), "<i2").reshape(-1,w.getnchannels())[:,0] / 32768.
    data = subprocess.check_output([imageio_ffmpeg.get_ffmpeg_exe(), "-v","error","-i",str(p),"-f","f32le","-"])
    decoded = np.frombuffer(data, "<f4")
    # Encoder delay is present in untagged CBR. Find it once on a non-silent section.
    start = int(np.argmax(abs(ref) > .04))
    length = min(8192, len(ref)-start-2500)
    if length < 1024:
        raise RuntimeError(f"{p.name}: no speech to compare")
    lag = int(np.argmax(np.correlate(decoded[start:start+length+2400],ref[start:start+length],"valid")))
    aligned = decoded[lag:lag+len(ref)]
    if len(aligned) != len(ref) or abs(len(decoded)-len(ref)) > 2600:
        raise RuntimeError(f"{p.name}: channel count or duration changed")
    snr = 10*np.log10(np.dot(ref,ref)/np.sum((aligned-ref)**2))
    bitrate = 8*p.stat().st_size/(len(decoded)/rate)
    if not 95000 < bitrate < 97000 or not snr > 20:
        raise RuntimeError(f"{p.name}: {bitrate:.0f} bit/s, {snr:.2f} dB")
    results[p.name] = dict(bytes=p.stat().st_size, seconds=len(ref)/rate, wav_to_mp3_snr_db=round(float(snr),2))
if len(results) != 5:
    raise RuntimeError("Expected five compact listening comparisons")
(root/"mp3-roundtrip.json").write_text(json.dumps(results,indent=2))
print(results)
