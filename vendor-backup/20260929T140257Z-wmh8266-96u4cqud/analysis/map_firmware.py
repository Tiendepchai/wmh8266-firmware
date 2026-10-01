#!/usr/bin/env python3
"""Offline image validation / address-accurate Intel HEX generation. No device I/O."""
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import struct

os.umask(0o077)
HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
SHA = '7e281748a3df4ede9d2bddefa04295ac3c8cba8d2504a15e4b752640c374b1f8'
b = (ROOT / 'original.bin').read_bytes()
assert len(b) == 1048576
assert hashlib.sha256(b).hexdigest() == SHA
assert b == (ROOT / 'verify-read.bin').read_bytes()


def image_v1(start):
    magic, count, mode, sizefreq, entry = struct.unpack_from('<BBBBI', b, start)
    assert magic == 0xe9 and 1 <= count <= 16
    p, checksum, segments = start + 8, 0xef, []
    for i in range(count):
        assert p + 8 <= len(b)
        address, size = struct.unpack_from('<II', b, p)
        p += 8
        assert 0 < size <= 0x100000 and p + size <= len(b)
        assert ((0x3ffe8000 <= address and address + size <= 0x40000000)
                or (0x40100000 <= address and address + size <= 0x40110000))
        for x in b[p:p+size]:
            checksum ^= x
        segments.append(dict(name=('iram' if address >= 0x40100000 else 'dram') + str(i),
                             flash_offset=p, address=address, size=size,
                             executable=address >= 0x40100000))
        p += size
    end = (p | 15) + 1
    assert end <= len(b) and b[end-1] == checksum
    assert any(s['executable'] and s['address'] <= entry < s['address'] + s['size'] for s in segments)
    return dict(start=start, end=end, entry=entry, flash_mode=mode,
                size_freq=sizefreq, xor_checksum=checksum, segments=segments)


def record(kind, address, payload):
    data = bytes([len(payload), address >> 8, address & 255, kind]) + payload
    return ':' + (data + bytes([-sum(data) & 255])).hex().upper() + '\n'


def write_hex(name, segments):
    lines = []
    for s in segments:
        a, p, n = s['address'], s['flash_offset'], s['size']
        upper = None
        while n:
            if a >> 16 != upper:
                upper = a >> 16
                lines.append(record(4, 0, struct.pack('>H', upper)))
            length = min(n, 16, 0x10000 - (a & 0xffff))
            lines.append(record(0, a & 0xffff, b[p:p+length]))
            a, p, n = a + length, p + length, n - length
    lines.append(record(1, 0, b''))
    (HERE / name).write_text(''.join(lines))

boot = image_v1(0)
start = 0x1000
magic, version, mode, sf, entry = struct.unpack_from('<BBBBI', b, start)
assert magic == 0xea and version == 4
zero, size = struct.unpack_from('<II', b, start + 8)
assert zero == 0 and size % 16 == 0 and start + 16 + size < len(b)
app = image_v1(start + 16 + size)
assert app['entry'] == entry
app['v2_start'] = start
app['v2_mode_sizefreq'] = [mode, sf]
irom = dict(name='irom', flash_offset=start+16, address=0x40200000+start+16,
            size=size, executable=True)
app['segments'].insert(0, irom)
crc = binascii.crc32(b[start:app['end']]) & 0xffffffff
crc = (crc ^ 0xffffffff) if crc & 0x80000000 else crc+1
stored = struct.unpack_from('<I', b, app['end'])[0]
assert crc == stored, (hex(crc), hex(stored))
app['crc32'] = crc
app['end_with_crc'] = app['end'] + 4
for image in [boot, app]:
    segs = sorted(image['segments'], key=lambda s: s['address'])
    assert all(x['address'] + x['size'] <= y['address'] for x,y in zip(segs,segs[1:]))
write_hex('boot.hex', boot['segments'])
write_hex('app.hex', app['segments'])
(HERE / 'app-image.bin').write_bytes(b[start:app['end_with_crc']])
metadata = dict(source_sha256=SHA, flash_size=len(b), boot=boot, app=app,
                mapping_note='IROM physical flash offset + 0x40200000; DRAM/IRAM from validated segment headers. Boot and app are separate programs, never overlapping imports.')
(HERE / 'memory-map.json').write_text(json.dumps(metadata, indent=2)+'\n')
safe = []
for needle in [b'RLOF',b'RLON',b'WMH-8266',b'RELAY ON',b'RELAY OFF',b'<form',b'POST',
               b'AT version:',b'SDK version:',b'+CIPSERVER',b'+CIPCLOSE',b'+CIPSEND',
               b'+CIPSTO',b'+UART',b'+IPD,',b'Invalid GPIO',b'HTTP/1.',b'Content-Length']:
    hits = []
    for m in re.finditer(re.escape(needle),b[:app['end_with_crc']]):
        off = m.start()
        seg = next((s for s in app['segments'] if s['flash_offset'] <= off < s['flash_offset']+s['size']),None)
        hits.append(dict(flash_offset=hex(off), cpu_address=hex(seg['address']+off-seg['flash_offset']) if seg else None))
    safe.append(dict(literal=needle.decode(),hits=hits))
(HERE / 'targeted-strings.json').write_text(json.dumps(safe,indent=2)+'\n')
print(json.dumps(metadata,indent=2))
print('Safe marker list written; all validation assertions passed.')
