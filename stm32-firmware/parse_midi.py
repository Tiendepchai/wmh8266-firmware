#!/usr/bin/env python3
import sys
import os

def parse_midi(filename):
    with open(filename, 'rb') as f:
        data = f.read()

    assert data[:4] == b'MThd', "Invalid MIDI header"
    division = int.from_bytes(data[12:14], 'big')

    idx = 14
    while idx < len(data) and data[idx:idx+4] != b'MTrk':
        chunk_len = int.from_bytes(data[idx+4:idx+8], 'big')
        idx += 8 + chunk_len

    assert data[idx:idx+4] == b'MTrk', "No MTrk chunk found"
    track_len = int.from_bytes(data[idx+4:idx+8], 'big')
    idx += 8
    end_idx = idx + track_len

    cur_tick = 0
    running_status = None
    tempo_us = 500000
    events = []

    def parse_vlq(i):
        val = 0
        while True:
            b = data[i]
            i += 1
            val = (val << 7) | (b & 0x7F)
            if not (b & 0x80):
                break
        return val, i

    while idx < end_idx:
        delta, idx = parse_vlq(idx)
        cur_tick += delta
        b = data[idx]
        if b & 0x80:
            status = b
            idx += 1
        else:
            status = running_status
        running_status = status

        if status == 0xFF:
            meta_type = data[idx]
            idx += 1
            meta_len, idx = parse_vlq(idx)
            meta_data = data[idx:idx+meta_len]
            idx += meta_len
            if meta_type == 0x51:
                tempo_us = int.from_bytes(meta_data, 'big')
        elif status in (0xF0, 0xF7):
            sysex_len, idx = parse_vlq(idx)
            idx += sysex_len
        else:
            cmd = status & 0xF0
            p1 = data[idx]
            p2 = data[idx+1] if cmd in (0x80, 0x90, 0xA0, 0xB0, 0xE0) else 0
            idx += 2 if cmd in (0x80, 0x90, 0xA0, 0xB0, 0xE0) else 1

            if cmd == 0x90 and p2 > 0:
                events.append((cur_tick, 'on', p1))
            elif cmd == 0x80 or (cmd == 0x90 and p2 == 0):
                events.append((cur_tick, 'off', p1))

    return division, tempo_us, events

def extract_monophonic_melody(division, tempo_us, events):
    ticks = sorted(list(set(e[0] for e in events)))
    if 0 not in ticks:
        ticks = [0] + ticks

    events_by_tick = {}
    for tick, typ, note in events:
        events_by_tick.setdefault(tick, []).append((typ, note))

    active_notes = set()
    raw_segments = []

    for i in range(len(ticks) - 1):
        t_start = ticks[i]
        t_end = ticks[i+1]

        if t_start in events_by_tick:
            for typ, note in sorted(events_by_tick[t_start], key=lambda x: 0 if x[0] == 'off' else 1):
                if typ == 'off':
                    active_notes.discard(note)
                elif typ == 'on':
                    active_notes.add(note)

        pitch = max(active_notes) if active_notes else 0
        raw_segments.append((t_start, t_end, pitch))

    # Merge adjacent segments with same pitch
    merged = []
    for t_start, t_end, pitch in raw_segments:
        if t_end == t_start:
            continue
        if merged and merged[-1][2] == pitch:
            merged[-1] = (merged[-1][0], t_end, pitch)
        else:
            merged.append((t_start, t_end, pitch))

    # Convert to notes list with freq_hz and duration_ms
    notes = []
    for t_start, t_end, pitch in merged:
        dur_ms = round((t_end - t_start) * (tempo_us / 1000.0) / division)
        if dur_ms == 0:
            continue
        freq_hz = round(440.0 * (2.0 ** ((pitch - 69) / 12.0))) if pitch > 0 else 0

        # Split durations > 65535 to fit uint16_t
        while dur_ms > 65535:
            notes.append((freq_hz, 65535))
            dur_ms -= 65535
        if dur_ms > 0:
            notes.append((freq_hz, dur_ms))

    return notes

def generate_header(notes, output_path):
    lines = [
        "#ifndef MELODY_H",
        "#define MELODY_H",
        "",
        "#include <stdint.h>",
        "",
        "typedef struct {",
        "    uint16_t freq_hz;",
        "    uint16_t duration_ms;",
        "} note_t;",
        "",
        "const note_t g_midi_melody[] = {"
    ]
    for freq, dur in notes:
        lines.append(f"    {{ {freq:5d}, {dur:5d} }},")
    lines.extend([
        "};",
        "",
        f"const uint16_t g_midi_melody_len = {len(notes)};",
        "",
        "#endif // MELODY_H",
        ""
    ])

    with open(output_path, 'w') as f:
        f.write("\n".join(lines))

def main():
    midi_file = "/Users/allah-computer/Downloads/melody.mid"
    output_header = "/Users/allah-computer/Research/Vibe-code-smarthome/stm32-wmh8266/melody.h"

    division, tempo_us, events = parse_midi(midi_file)
    notes = extract_monophonic_melody(division, tempo_us, events)
    generate_header(notes, output_header)
    print(f"Generated {output_header} with {len(notes)} notes.")

if __name__ == '__main__':
    main()
