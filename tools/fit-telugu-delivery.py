"""Fit conservative word-level joy and sadness cues using the pinned Telugu_Emotion CSV splits.

Dataset CC BY 4.0, Mounika Marreddy et al. (ACM TALLIP, 2022).
https://huggingface.co/datasets/mounikaiiith/Telugu_Emotion/tree/6432e1357ed6ad57db1683451b08943639eb45ae
Usage: python3 tools/fit-telugu-delivery.py <CSV folder> <output TSV>
"""
import csv
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

root = Path(sys.argv[1])
def rows(split):
    return list(csv.DictReader(next(root.glob('*' + split + '*.csv')).open(encoding='utf-8-sig')))

def words(s):
    return set(re.findall(r'[ఁ-౿]+', s.replace('\u200c','').replace('\u200d','')))

train = rows('train')
seen = {r['Sentence'].strip() for r in train}
counts = defaultdict(Counter)
for row in train:
    for word in words(row['Sentence']):
        counts[word][row['Emotion']] += 1

candidates = {}
for word, count in counts.items():
    label, hits = count.most_common(1)[0]
    if label in ('happy', 'sad') and hits >= 10:
        candidates[word] = (hits / (sum(count.values()) + 2), label)

def evaluate(data, cutoff):
    correct = called = 0
    for r in data:
        matches = [candidates[w] for w in words(r['Sentence']) if w in candidates and candidates[w][0] >= cutoff]
        if matches and len({label for _, label in matches}) == 1:
            called += 1
            correct += matches[0][1] == r['Emotion']
    return correct, called

valid = [r for r in rows('valid') if r['Sentence'].strip() not in seen]
# Prefer the highest coverage that attains 80% validation precision with at least 50 decisions.
cutoff = next((t for t in (.65,.70,.75,.80,.85,.90) if (lambda x: x[1]>=50 and x[0]/x[1]>=.80)(evaluate(valid,t))), .90)
Path(sys.argv[2]).write_text(''.join(f'{w}\t{label}\n' for w,(score,label) in sorted(candidates.items()) if score >= cutoff), encoding='utf-8')
seen.update(r['Sentence'].strip() for r in valid)
test = [r for r in rows('test') if r['Sentence'].strip() not in seen]
print('train',len(train),'threshold',cutoff,'test rows',len(test),'correct, classified',evaluate(test,cutoff))
