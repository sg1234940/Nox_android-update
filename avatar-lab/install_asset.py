#!/usr/bin/env python3
"""Extract the exact reviewed model from the standalone APK without executing it."""
import argparse
import hashlib
import os
from pathlib import Path
import tempfile
import zipfile

SHA256 = '451ebc87620b95500163716e77b7433d6ef7d765a2b33391a0df08f2b921848c'

def install(apk, destination):
    destination = Path(destination)
    if destination.exists():
        if hashlib.sha256(destination.read_bytes()).hexdigest() != SHA256:
            raise ValueError('Destination contains a different model; move it before installation.')
        return 'already installed'
    with zipfile.ZipFile(apk) as archive:
        info = archive.getinfo('assets/avatar/iskra.glb')
        if not 1 <= info.file_size <= 100 * 1024 * 1024:
            raise ValueError('Unexpected model size')
        data = archive.read(info)
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise ValueError('APK contains a different model')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as out:
            temporary = Path(out.name); out.write(data); out.flush(); os.fsync(out.fileno())
        os.replace(temporary, destination)
    finally:
        if temporary is not None: temporary.unlink(missing_ok=True)
    return 'installed'

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('apk', type=Path); args = parser.parse_args()
    target = Path(__file__).resolve().parent.parent / 'avatar-test/src/main/assets/avatar/iskra.glb'
    print(install(args.apk, target))
