#!/usr/bin/env python3
"""Android uses ICU regex, unlike host java.util.regex/Robolectric. Check shipped markup patterns."""
import ctypes, ctypes.util, json, pathlib, re
source=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/java/in/aitechapp/teluguoffline/SpeechMarkup.java'
libname=ctypes.util.find_library('icui18n')
if not libname: raise SystemExit('Install libicu-dev to run Android regex regression')
lib=ctypes.CDLL(libname)
version=re.search(r'\.so\.(\d+)',libname)
suffix='_'+version.group(1) if version else ''
open_regex=getattr(lib,'uregex_open'+suffix); close_regex=getattr(lib,'uregex_close'+suffix)
open_regex.restype=ctypes.c_void_p
open_regex.argtypes=[ctypes.c_void_p,ctypes.c_int32,ctypes.c_uint32,ctypes.c_void_p,ctypes.POINTER(ctypes.c_int32)]
close_regex.argtypes=[ctypes.c_void_p]
def compile_regex(pattern):
    data=pattern.encode('utf-16-le'); buf=ctypes.create_string_buffer(data)
    error=ctypes.c_int32(0); detail=ctypes.create_string_buffer(128)
    compiled=open_regex(buf,len(data)//2,0,detail,ctypes.byref(error))
    if compiled: close_regex(compiled)
    return error.value
# Demonstrate that this test catches the actual Android-only crash (JVM accepts it).
assert compile_regex(r'\{([^{}]*)}')>0, 'Expected ICU to reject the old unescaped closing brace'
literals=re.findall(r'(?:Pattern\.compile|replaceAll)\(\s*("(?:\\.|[^"\\])*")',source.read_text())
assert literals
for literal in literals:
    pattern=json.loads(literal)
    assert compile_regex(pattern)<=0, f'Android ICU rejected {pattern!r}'
print(f'PASS: {len(literals)} actual SpeechMarkup regexes compile on ICU; old bug rejected')
