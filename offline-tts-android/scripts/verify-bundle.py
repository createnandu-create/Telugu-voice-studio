#!/usr/bin/env python3
"""Fail the build if a shipped native library, phoneme or token file changes unexpectedly."""
import hashlib,json,pathlib,zipfile
root=pathlib.Path(__file__).resolve().parents[1]/'app/src/main'
manifest=json.loads((root/'assets/bundle-sha256.json').read_text())
for name,expected in manifest.items():
 assert hashlib.sha256((root/name).read_bytes()).hexdigest()==expected,name
with zipfile.ZipFile(root/'assets/phonemes.zip') as z:
 hashes=json.loads((root/'assets/phonemes-sha256.json').read_text())
 assert set(z.namelist())==set(hashes)
 for name,expected in hashes.items():assert hashlib.sha256(z.read(name)).hexdigest()==expected,name
assert not list((root/'assets').rglob('*.onnx')), 'Voice weights must remain downloadable'
print(f'PASS: {len(manifest)} bundled files; {len(hashes)} phoneme files; no bundled voice weights')
