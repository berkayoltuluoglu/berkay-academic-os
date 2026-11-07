#!/usr/bin/env python3
"""Build a Linux x64 desktop release with its own Java 21 runtime and installer."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile

REPOSITORY = Path(__file__).resolve().parents[1]
BUILD = REPOSITORY / 'app/build/linux'
MODULES = ('java.se,jdk.crypto.ec,jdk.unsupported,jdk.xml.dom,jdk.charsets,'
           'jdk.localedata,jdk.security.auth,jdk.naming.dns,jdk.zipfs,jdk.net,jdk.httpserver')


def sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64'))
    parser.add_argument('--skip-gradle', action='store_true')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    if platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'amd64'):
        parser.error('Bu paket Linux x64 üzerinde üretilmelidir.')
    java_home = args.java_home.resolve()
    runtime_version = subprocess.check_output([str(java_home / 'bin/java'), '-version'], stderr=subprocess.STDOUT, text=True)
    if 'version "21.' not in runtime_version:
        parser.error('--java-home bir Java 21 JDK klasörünü göstermeli.')
    if not args.skip_gradle:
        environment = dict(os.environ, JAVA_HOME=str(java_home))
        command = [str(REPOSITORY / 'gradlew'), '-Dorg.gradle.java.home=' + str(java_home), ':app:installDist', '--no-daemon']
        if args.offline:
            command.append('--offline')
        subprocess.run(command, cwd=REPOSITORY, env=environment, check=True)
    version = json.loads((REPOSITORY / 'app/src/main/resources/config.json').read_text())['app']['version']
    libraries = REPOSITORY / 'app/build/install/app/lib'
    jars = sorted(libraries.glob('*.jar'))
    if not (libraries / f'app-{version}.jar').is_file() or not any(p.name.endswith('-linux.jar') for p in jars):
        raise RuntimeError('Linux dağıtımı eksik; önce :app:installDist çalıştır.')
    if any(p.name.endswith(('-win.jar', '-mac.jar', '-linux-aarch64.jar')) for p in jars):
        raise RuntimeError('Linux x64 dışındaki JavaFX bağımlılığı pakete alınamaz.')
    name = f'BerkayAcademicOS-{version}-linux-x64'
    payload = BUILD / 'payload' / name
    if payload.exists():
        shutil.rmtree(payload)
    (payload / 'app/lib').mkdir(parents=True)
    for jar in jars:
        shutil.copy2(jar, payload / 'app/lib' / jar.name)
    for filename in ('academic-os', 'academic-os-cli', 'academic-os.svg', 'install.sh'):
        shutil.copy2(REPOSITORY / 'packaging/linux' / filename, payload / filename)
    shutil.copy2(REPOSITORY / 'packaging/install_linux.py', payload / 'install_linux.py')
    shutil.copy2(REPOSITORY / 'packaging/LINUX.md', payload / 'LINUX.md')
    shutil.copytree(REPOSITORY / 'app/src/main/resources/agent-guide', payload / 'agent-guide')
    (payload / 'defaults/scripts').mkdir(parents=True)
    shutil.copy2(REPOSITORY / 'scripts/test_script.py', payload / 'defaults/scripts/test_script.py')
    for name in ('academic-os', 'academic-os-cli', 'install.sh'):
        (payload / name).chmod(0o755)
    subprocess.run([str(java_home / 'bin/jlink'), '--add-modules', MODULES,
                    '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=zip-6',
                    '--output', str(payload / 'runtime')], check=True)
    subprocess.run([str(payload / 'academic-os'), '--self-check'], cwd='/tmp', check=True)
    manifest = {
        'application': 'Berkay Academic OS', 'version': version, 'platform': 'linux-x64',
        'runtime': runtime_version.splitlines()[0], 'runtimeModules': MODULES.split(','),
        'files': {p.relative_to(payload).as_posix(): sha256(p) for p in sorted(payload.rglob('*')) if p.is_file()},
    }
    (payload / 'package-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    output = BUILD / 'dist'
    output.mkdir(parents=True, exist_ok=True)
    archive = output / (payload.name + '.tar.gz')
    with tarfile.open(archive, 'w:gz', dereference=True) as target:
        target.add(payload, arcname=payload.name)
    digest = sha256(archive)
    (output / (archive.name + '.sha256')).write_text(digest + '  ' + archive.name + '\n')
    print(f'Paket: {archive}\nBoyut: {archive.stat().st_size / 1024**2:.1f} MiB\nSHA-256: {digest}')


if __name__ == '__main__':
    main()
