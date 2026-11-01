"""Check the packaged screen code, not just source, version metadata or JVM classes.

Regression for an APK whose manifest changed while stale screen DEX was retained.
Requires the official Android build-tools dexdump binary.
"""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def check(apk, dexdump, version):
    descriptor = "Lpl/iskra/avatar/test/AvatarTestActivity;"
    found = []
    with tempfile.TemporaryDirectory(prefix='lab-dex-check-') as temp:
        with zipfile.ZipFile(apk) as archive:
            for name in archive.namelist():
                if not re.fullmatch(r'classes\d*\.dex', name):
                    continue
                data = archive.read(name)
                if descriptor.encode() not in data:
                    continue
                path = Path(temp) / name
                path.write_bytes(data)
                dump = subprocess.check_output([str(dexdump), '-d', str(path)], text=True, errors='replace')
                for block in re.split(r'\nClass #\d+', dump):
                    if re.search(r"Class descriptor\s+: '" + re.escape(descriptor) + "'", block):
                        found.append(block)
    assert len(found) == 1, 'Expected exactly one packaged Activity definition'
    screen = found[0]
    methods = re.split(r'\n    #\d+', screen)
    status = next(m for m in methods if re.search(r"name\s+: 'sayStatus'", m))
    assert '.setVisibility:' in status, 'Stale DEX: sayStatus does not hide empty status'
    ready = [m for m in methods if 'const-string' in m and '"Model gotowy"' in m]
    assert len(ready) == 1, 'Missing ready-status handler'
    assert '.sayStatus:' in ready[0], 'Stale DEX: ready handler bypasses hide policy'
    assert '.setText:' not in ready[0], 'Stale DEX: ready handler writes banner directly'
    assert '"Iskra Lab ' + version + '"' in screen, 'Visible build version does not match'
    controls = next(m for m in methods if re.search(r"name\s+: 'manualControls'", m))
    assert 'Landroid/widget/SeekBar;' in controls, 'Controls must expose inline live sliders'
    assert 'Landroid/app/AlertDialog' not in controls, 'Controls must not obscure the avatar with a dialog'
    assert '.sliderDialog:' not in controls, 'Controls must not open nested slider dialogs'
    assert '.layoutControls:' in controls, 'Controls must reserve a visible avatar viewport'
    viewport = next(m for m in methods if re.search(r"name\s+: 'layoutControls'", m))
    assert viewport.count('.setLayoutParams:') >= 2, 'Panel and avatar viewport must resize separately'
    if version == '0.6.4':
        chat = next(m for m in methods if re.search(r"name\s+: 'chatSettings'", m))
        assert 'Przezroczystość tła' in chat and 'Wysokość czatu' in chat
        assert '.layoutChat:' in screen and '.applyChatAppearance:' in screen
        layout = next(m for m in methods if re.search(r"name\s+: 'layoutChat'", m))
        assert '.getHeight:' in layout and '.setLayoutParams:' in layout
    print('PASS: packaged screen hides ready status and contains visible build ' + version)
    print('PASS: packaged manual controls use inline sliders and reserve the avatar viewport')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('--dexdump', required=True, type=Path)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    check(args.apk, args.dexdump, args.version)
