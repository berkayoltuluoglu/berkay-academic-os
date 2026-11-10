#!/usr/bin/env python3
"""Build a self-contained Windows x64 EXE installer on Linux or Windows."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import urllib.request
import zipfile

REPOSITORY = Path(__file__).resolve().parents[1]
BUILD = REPOSITORY / 'app/build/windows'


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def download(address: str, target: Path) -> None:
    request = urllib.request.Request(address, headers={'User-Agent': 'AcademicOS-Packager/1.5.1'})
    with urllib.request.urlopen(request, timeout=120) as source, target.open('wb') as output:
        shutil.copyfileobj(source, output)


def runtime(cache: Path) -> tuple[Path, dict]:
    address = 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse'
    request = urllib.request.Request(address, headers={'User-Agent': 'AcademicOS-Packager/1.5.1'})
    with urllib.request.urlopen(request, timeout=60) as response:
        choices = json.load(response)
    choice = choices[0]
    package = choice['binary']['package']
    target = cache / package['name']
    cache.mkdir(parents=True, exist_ok=True)
    if not target.exists() or sha256(target) != package['checksum']:
        partial = target.with_suffix('.part')
        download(package['link'], partial)
        if sha256(partial) != package['checksum']:
            partial.unlink()
            raise RuntimeError('The Windows Java runtime checksum does not match Adoptium metadata.')
        partial.replace(target)
    return target, {'version': choice['version']['semver'], 'sha256': package['checksum'], 'source': package['link']}


def extract_runtime(archive: Path, destination: Path) -> None:
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            path = PurePosixPath(item.filename)
            if path.is_absolute() or '..' in path.parts or len(path.parts) < 2:
                if item.is_dir() and len(path.parts) == 1:
                    continue
                raise RuntimeError('Unsafe runtime archive entry.')
            target = destination.joinpath(*path.parts[1:])
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(item) as stream, target.open('wb') as output:
                    shutil.copyfileobj(stream, output)
    if not all((destination / name).is_file() for name in ('bin/javaw.exe', 'bin/java.exe')):
        raise RuntimeError('The downloaded runtime must contain Windows javaw.exe and java.exe for the desktop and CLI.')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--makensis', default=shutil.which('makensis'))
    parser.add_argument('--nsis-dir', type=Path)
    parser.add_argument('--java-home', type=Path, default=os.getenv('JAVA_HOME'))
    parser.add_argument('--skip-gradle', action='store_true')
    parser.add_argument('--google-client', type=Path, help='Publisher desktop OAuth client JSON, explicitly authorized for inclusion; never a token store.')
    args = parser.parse_args()
    if not args.makensis:
        parser.error('NSIS 3.x makensis is required (--makensis /path/to/makensis).')
    version = json.loads((REPOSITORY / 'app/src/main/resources/config.json').read_text(encoding='utf-8'))['app']['version']
    if not args.skip_gradle:
        gradle = str(REPOSITORY / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
        command = [gradle]
        if args.java_home:
            command += ['-Dorg.gradle.java.home=' + str(args.java_home)]
        command += [':app:stageWindows', '-PjavafxPlatform=win', '--no-daemon']
        subprocess.run(command, cwd=REPOSITORY, check=True)
    libraries = BUILD / 'input/lib'
    jars = sorted(libraries.glob('*.jar'))
    if not jars or not any('javafx-graphics-' in path.name and path.name.endswith('-win.jar') for path in jars):
        raise RuntimeError('Windows JavaFX dependencies are missing; run :app:stageWindows -PjavafxPlatform=win.')
    if any('-linux' in path.name or '-mac' in path.name for path in jars):
        raise RuntimeError('The Windows payload contains a non-Windows JavaFX dependency.')
    if [path.name for path in jars if path.name.startswith('app-')] != [f'app-{version}.jar']:
        raise RuntimeError('The staged application JAR does not match the release version; rebuild :app:stageWindows.')
    # Keep older release artifacts (including any previously authorized publisher client) intact.
    payload = BUILD / f'payload-{version}'
    if payload.exists():
        shutil.rmtree(payload)
    payload.mkdir(parents=True)
    shutil.copy2(REPOSITORY / 'packaging/academic-os.cmd', payload / 'academic-os.cmd')
    shutil.copytree(REPOSITORY / 'app/src/main/resources/agent-guide', payload / 'agent-guide')
    shutil.copy2(REPOSITORY / 'packaging/WINDOWS.md', payload / 'WINDOWS.md')
    (payload / 'app/lib').mkdir(parents=True)
    for jar in jars:
        shutil.copy2(jar, payload / 'app/lib' / jar.name)
    if args.google_client:
        client = json.loads(args.google_client.read_text(encoding='utf-8-sig')).get('installed', {})
        if not all(isinstance(client.get(key), str) and client[key].strip() for key in ('client_id', 'client_secret')):
            raise RuntimeError('Only a Google Desktop application OAuth client can be bundled.')
        sanitized = {'installed': {'client_id': client['client_id'], 'client_secret': client['client_secret'],
                      'auth_uri': 'https://accounts.google.com/o/oauth2/auth', 'token_uri': 'https://oauth2.googleapis.com/token',
                      'redirect_uris': ['http://localhost']}}
        (payload / 'app/google-oauth-client.json').write_text(json.dumps(sanitized, indent=2), encoding='utf-8')
    archive, runtime_info = runtime(BUILD / 'downloads')
    extract_runtime(archive, payload / 'runtime')
    manifest = {'application': 'Berkay Academic OS', 'version': version, 'platform': 'windows-x64', 'runtime': runtime_info,
                'googleClientBundled': bool(args.google_client),
                'libraries': [{'name': path.name, 'sha256': sha256(path)} for path in jars]}
    environment = os.environ.copy()
    if args.nsis_dir:
        environment['NSISDIR'] = str(args.nsis_dir.resolve())
    prefix = '/D' if os.name == 'nt' else '-D'
    verbosity = '/V2' if os.name == 'nt' else '-V2'
    launcher = payload / 'BerkayAcademicOS.exe'
    subprocess.run([args.makensis, verbosity, prefix + 'OUTPUT=' + str(launcher), str(REPOSITORY / 'packaging/windows-launcher.nsi')], env=environment, check=True)
    manifest['files'] = {path.relative_to(payload).as_posix(): sha256(path)
                         for path in sorted(payload.rglob('*')) if path.is_file()}
    (payload / 'package-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    output = BUILD / 'dist'
    output.mkdir(parents=True, exist_ok=True)
    installer = output / f'BerkayAcademicOS-{version}-setup.exe'
    subprocess.run([args.makensis, verbosity, prefix + 'APP_VERSION=' + version, prefix + 'OUTPUT=' + str(installer),
                    prefix + 'PAYLOAD=' + str(payload), str(REPOSITORY / 'packaging/windows-setup.nsi')], env=environment, check=True)
    digest = sha256(installer)
    (output / (installer.name + '.sha256')).write_text(digest + '  ' + installer.name + '\n', encoding='ascii')
    shutil.copy2(REPOSITORY / 'packaging/WINDOWS.md', output / f'BerkayAcademicOS-{version}-Windows-Kullanim.md')
    print(f'Installer: {installer}\nSHA-256: {digest}\nSize: {installer.stat().st_size / 1024 / 1024:.1f} MB')


if __name__ == '__main__':
    main()
