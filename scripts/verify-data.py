#!/usr/bin/env python3
"""Verify the uploaded callsign lists remain byte-identical to their recorded originals."""
import hashlib
import json
from pathlib import Path
root = Path(__file__).resolve().parent.parent
for entry in json.loads((root / 'docs/DATA_MANIFEST.json').read_text()):
    data = (root / 'app/src/main/assets/calls' / entry['file']).read_bytes()
    assert hashlib.sha256(data).hexdigest() == entry['sha256'], entry['file']
    lines = [s.strip() for s in data.decode('ascii').splitlines() if s.strip() and not s.lstrip().startswith('#')]
    assert len(lines) == len(set(lines)) == entry['records'], entry['file']
    print(f"PASS: {entry['file']} {len(lines):,} calls; SHA-256 unchanged")
