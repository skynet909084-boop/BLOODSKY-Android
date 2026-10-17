"""Build a shell-only DEX helper for real FINGER input in the disposable API30 emulator."""
import os
from pathlib import Path
import subprocess
import zipfile


def build():
    sdk = Path(os.environ['ANDROID_HOME'])
    root = Path(__file__).resolve().parents[1]
    output = root / 'build/ui-input'
    classes = output / 'classes'
    dex = output / 'dex'
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(parents=True, exist_ok=True)
    android = sdk / 'platforms/android-35/android.jar'
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(android), '-d', str(classes),
                    str(root / 'tools/android/BloodskyFingerTap.java')], check=True)
    subprocess.run([str(sdk / 'build-tools/35.0.0/d8'), '--min-api', '23', '--lib', str(android),
                    '--output', str(dex), str(classes / 'BloodskyFingerTap.class')], check=True)
    target = output / 'bloodsky-ui-input.jar'
    with zipfile.ZipFile(target, 'w') as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
    print(target)


if __name__ == '__main__':
    build()
