#!/usr/bin/env python3
"""Host oracle: repaired graph == original graph fed Piper's correct BOS/PAD tokens.
Usage: python verify-piper-repair.py ORIGINAL_DIR REPAIRED_DIR OUTPUT_JSON
Requires onnx, onnxruntime, numpy. No downloads or uploads. Repaired filenames: VOICE-fixed.onnx.
"""
import hashlib,json,pathlib,sys
import numpy as np
import onnx,onnxruntime as rt
rows=[]
for voice in ['venkatesh','padmavathi']:
 original=pathlib.Path(sys.argv[1])/f'{voice}.onnx'; fixed=pathlib.Path(sys.argv[2])/f'{voice}-fixed.onnx'
 old=onnx.load(original);new=onnx.load(fixed);onnx.checker.check_model(new)
 assert [x.SerializeToString()for x in old.graph.initializer]==[x.SerializeToString()for x in new.graph.initializer[:-5]]
 # n ˈ a m a s k ˌ a ː r a n, padded per phoneme, BOS then EOS (sherpa legacy input).
 ids=[1,26,0,120,0,14,0,25,0,14,0,31,0,23,0,121,0,14,0,122,0,30,0,14,0,26,0,2]
 correct=ids[:1]+[0]+ids[1:]
 opts=rt.SessionOptions();opts.intra_op_num_threads=2
 a=rt.InferenceSession(old.SerializeToString(),sess_options=opts,providers=['CPUExecutionProvider'])
 b=rt.InferenceSession(new.SerializeToString(),sess_options=opts,providers=['CPUExecutionProvider'])
 def render(session,tokens):return session.run(None,{'input':np.array([tokens],np.int64),'input_lengths':np.array([len(tokens)],np.int64),'scales':np.array([0,1,0],np.float32)})[0]
 x=render(a,correct);y=render(b,ids)
 assert x.shape==y.shape and np.max(np.abs(x-y))<1e-6
 rows.append(dict(voice=voice,original_sha256=hashlib.sha256(original.read_bytes()).hexdigest(),repaired_sha256=hashlib.sha256(fixed.read_bytes()).hexdigest(),unchanged_weight_initializers=len(old.graph.initializer),legacy_ids=ids,correct_ids=correct,frames=x.size,max_abs_output_difference=float(np.max(np.abs(x-y))),note='Noise scales zero for deterministic equivalence only, not production quality. Physical phone and listening untested.'))
pathlib.Path(sys.argv[3]).write_text(json.dumps(rows,indent=2))
print('PASS: both repaired graphs reproduce correct Piper input exactly; learned weights unchanged')
