#!/usr/bin/env python3
"""Extract the exact reviewed model from the standalone APK without executing it."""
import argparse
import hashlib
import os
from pathlib import Path
import tempfile
import zipfile

from cheek_rig import SOURCE_SHA, add_cheeks

SHA256 = '0f1d4325bcda5504acee3cfe57b35797eb8c15f1cf5fed1cb0dd70286577b4cc'

def install(apk, destination):
    destination = Path(destination)
    if destination.exists():
        data = destination.read_bytes()
        existing = hashlib.sha256(data).hexdigest()
        if existing == SHA256:
            return 'already installed'
        if existing != SOURCE_SHA:
            raise ValueError('Destination contains a different model; move it before installation.')
    else:
        with zipfile.ZipFile(apk) as archive:
            info = archive.getinfo('assets/avatar/iskra.glb')
            if not 1 <= info.file_size <= 100 * 1024 * 1024:
                raise ValueError('Unexpected model size')
            data = archive.read(info)
    if hashlib.sha256(data).hexdigest() == SOURCE_SHA:
        data = add_cheeks(data)
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise ValueError('Unexpected avatar model')
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
