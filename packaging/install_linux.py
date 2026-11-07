#!/usr/bin/env python3
"""Install the bundled desktop release for the current user; keep profiles outside releases."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import shlex
import subprocess
import sys
import tempfile
import uuid
import zipfile

APP_ID = 'berkay-academic-os'


def sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_package(bundle: Path) -> dict:
    manifest = json.loads((bundle / 'package-manifest.json').read_text())
    if manifest.get('application') != 'Berkay Academic OS' or manifest.get('platform') != 'linux-x64':
        raise RuntimeError('Bu bir Academic OS Linux paketi değil.')
    if not re.fullmatch(r'\d+\.\d+\.\d+', manifest.get('version', '')):
        raise RuntimeError('Geçersiz paket sürümü.')
    for name, digest in manifest['files'].items():
        path = PurePosixPath(name)
        if path.is_absolute() or '..' in path.parts or not path.parts:
            raise RuntimeError('Geçersiz paket dosyası.')
        target = bundle / name
        if not target.resolve().is_relative_to(bundle.resolve()) or not target.is_file() or sha256(target) != digest:
            raise RuntimeError('Paket doğrulaması başarısız: ' + name)
    for name in ('academic-os', 'runtime/bin/java', 'install_linux.py', 'academic-os.svg'):
        if name not in manifest['files']:
            raise RuntimeError('Eksik paket bileşeni: ' + name)
    return manifest


@contextmanager
def profile_lock(root: Path):
    """Use POSIX record locks, matching Java FileChannel rather than BSD flock."""
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (root / '.instance.lock').open('a+b') as lock:
        try:
            fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Academic OS açık. Önce sistem tepsisinden tamamen çık.') from None
        try:
            yield
        finally:
            fcntl.lockf(lock, fcntl.LOCK_UN)


def import_profile(source: Path, destination: Path) -> None:
    source = source.resolve()
    if not (source / 'data').is_dir():
        raise RuntimeError('Aktarılacak profilde data klasörü bulunamadı.')
    if destination.exists():
        raise RuntimeError('Hedef profil zaten var; mevcut verilerin üzerine aktarım yapılmadı.')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with profile_lock(source):
        stage = Path(tempfile.mkdtemp(prefix='.academic-os-import-', dir=destination.parent))
        try:
            snapshots = []
            for directory in ('data', 'notes', 'config', 'scripts'):
                base = source / directory
                if not base.exists():
                    continue
                if base.is_symlink():
                    raise RuntimeError('Profil klasörü sembolik bağlantı olamaz: ' + directory)
                for file in sorted(base.rglob('*')):
                    if file.is_symlink():
                        raise RuntimeError('Profilde sembolik bağlantı var: ' + str(file.relative_to(source)))
                    name = file.relative_to(source).as_posix()
                    if not file.is_file() or file.name.endswith('.lock') or name == 'config/profile-location.properties':
                        continue
                    if name.startswith('data/cache/') or '/.academic-os-' in name:
                        continue
                    target = stage / name
                    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                    digest = sha256(file)
                    shutil.copyfile(file, target)
                    target.chmod(0o600)
                    if sha256(target) != digest:
                        raise RuntimeError('Aktarım sırasında kaynak değişti; yeniden dene.')
                    snapshots.append((file, name, digest))
            # A normal in-app backup excludes Google credentials and token stores.
            entries = [{'path': name, 'size': (stage / name).stat().st_size, 'sha256': digest}
                       for _, name, digest in snapshots
                       if name.startswith(('data/', 'notes/')) or name == 'config/app-config.json']
            created = datetime.now(timezone.utc)
            backup = stage / 'backups' / ('before-linux-install-' + created.strftime('%Y%m%dT%H%M%SZ') + '.zip')
            backup.parent.mkdir(mode=0o700)
            with zipfile.ZipFile(backup, 'w', zipfile.ZIP_DEFLATED) as output:
                output.writestr('manifest.json', json.dumps({'schemaVersion': 1, 'createdAt': created.isoformat(), 'files': entries}))
                for entry in entries:
                    output.write(stage / entry['path'], entry['path'])
            for file, _, digest in snapshots:
                if sha256(file) != digest:
                    raise RuntimeError('Aktarım sırasında kaynak değişti; yeniden dene.')
            stage.rename(destination)
        finally:
            if stage.exists():
                shutil.rmtree(stage)


def desktop_entry(launcher: Path, icon: Path) -> str:
    def quoted(value: Path) -> str:
        text = str(value)
        if '\n' in text or '\r' in text:
            raise RuntimeError('Kurulum yolunda satır sonu olamaz.')
        for character in ('\\', '"', '`', '$'):
            text = text.replace(character, '\\' + character)
        return '"' + text.replace('\\', '\\\\').replace('%', '%%') + '"'
    icon_name = str(icon).replace('\\', '\\\\')
    return ('[Desktop Entry]\nType=Application\nVersion=1.0\nName=Berkay Academic OS\n'
            'Comment=Dersler, görevler, ajanda ve finans\n'
            f'Exec={quoted(launcher)}\nIcon={icon_name}\nTerminal=false\n'
            'Categories=Office;Calendar;\nStartupNotify=true\nStartupWMClass=org.example.App\n')


def desktop_directory() -> Path | None:
    if shutil.which('xdg-user-dir'):
        result = subprocess.run(['xdg-user-dir', 'DESKTOP'], capture_output=True, text=True)
        if result.returncode == 0 and result.stdout.strip():
            path = Path(result.stdout.strip())
            if path.is_dir() and path.resolve() != Path.home().resolve():
                return path
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument('--prefix', type=Path, default=Path.home() / '.local/opt' / APP_ID)
    parser.add_argument('--import-profile', type=Path)
    parser.add_argument('--desktop-dir', type=Path)
    parser.add_argument('--no-desktop', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    bundle = args.package.resolve()
    manifest = verify_package(bundle)
    prefix = args.prefix.expanduser().absolute()
    data_home = Path(os.environ.get('XDG_DATA_HOME', str(Path.home() / '.local/share')))
    profile = data_home / APP_ID
    desktop = None if args.no_desktop else (args.desktop_dir or desktop_directory())
    release = prefix / 'releases' / manifest['version']
    print(f"Sürüm: {manifest['version']}\nProgram: {release}\nKullanıcı verileri: {profile}")
    if args.import_profile:
        print('Mevcut profil kopyalanacak: ' + str(args.import_profile.resolve()))
        if profile.exists():
            raise RuntimeError('Hedef profil zaten var; üzerine aktarım yapılmadı.')
    if desktop:
        print('Masaüstü kısayolu: ' + str(desktop / (APP_ID + '.desktop')))
    if args.dry_run:
        return
    prefix.mkdir(parents=True, exist_ok=True)
    with (prefix / '.install.lock').open('a+b') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if profile.exists():
            # Follow a profile relocation selected inside Settings.
            active = profile
            location = profile / 'location.properties'
            if location.exists():
                # Java is the authority for decoding .properties escapes and checking its instance lock.
                result = subprocess.run([str(bundle / 'academic-os'), '--check-instance'], capture_output=True)
                if result.returncode:
                    raise RuntimeError('Profil kullanımda; uygulamadan tamamen çıkıp yeniden kur.')
            with profile_lock(active):
                install_release(bundle, release, manifest)
        else:
            install_release(bundle, release, manifest)
        if args.import_profile:
            import_profile(args.import_profile, profile)
        else:
            profile.mkdir(parents=True, exist_ok=True, mode=0o700)
        # Seed the Linux example script only if the user has no copy.
        script = profile / 'scripts/test_script.py'
        if not script.exists():
            script.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(release / 'defaults/scripts/test_script.py', script)
        pointer = prefix / ('.current-' + uuid.uuid4().hex)
        pointer.symlink_to(Path('releases') / manifest['version'], target_is_directory=True)
        pointer.replace(prefix / 'current')
        # CLI lives on the user's normal local PATH; the desktop entry keeps its GUI launcher.
        cli_source = release / 'academic-os-cli'
        if cli_source.is_file():
            bin_directory = Path.home() / '.local/bin'
            bin_directory.mkdir(parents=True, exist_ok=True)
            cli_target = bin_directory / 'academic-os'
            marker = '# Academic OS planning CLI'
            if cli_target.exists() and marker not in cli_target.read_text():
                raise RuntimeError('~/.local/bin/academic-os başka bir programa ait; dosya korunuyor.')
            content_cli = '#!/bin/sh\n' + marker + '\nexec ' + shlex.quote(str(prefix / 'current/academic-os-cli')) + ' "$@"\n'
            staged_cli = bin_directory / ('.academic-os-' + uuid.uuid4().hex)
            staged_cli.write_text(content_cli)
            staged_cli.chmod(0o755)
            staged_cli.replace(cli_target)
        guide = release / 'agent-guide'
        if guide.is_dir():
            for source in guide.iterdir():
                target = profile / ('examples/' if source.name == 'planner-proposal.json' else '') / source.name
                if not target.exists():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
        applications = data_home / 'applications' 
        applications.mkdir(parents=True, exist_ok=True)
        content = desktop_entry(prefix / 'current/academic-os', prefix / 'current/academic-os.svg')
        entry = applications / (APP_ID + '.desktop')
        entry.write_text(content)
        entry.chmod(0o755)
        if desktop:
            desktop.mkdir(parents=True, exist_ok=True)
            shortcut = desktop / entry.name
            shutil.copyfile(entry, shortcut)
            shortcut.chmod(0o755)
            if shutil.which('gio'):
                subprocess.run(['gio', 'set', str(shortcut), 'metadata::trusted', 'true'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if shutil.which('update-desktop-database'):
            subprocess.run(['update-desktop-database', str(applications)], check=False)
    print('Kurulum tamamlandı. Uygulama menüsünden Berkay Academic OS ile açabilirsin.')


def install_release(bundle: Path, release: Path, manifest: dict) -> None:
    if release.exists():
        if verify_package(release) != manifest:
            raise RuntimeError('Aynı sürümde farklı bir kurulum var; mevcut program korunuyor.')
        return
    release.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='.install-', dir=release.parent))
    try:
        shutil.copytree(bundle, stage, dirs_exist_ok=True)
        verify_package(stage)
        subprocess.run([str(stage / 'academic-os'), '--self-check'], check=True)
        stage.rename(release)
    finally:
        if stage.exists():
            shutil.rmtree(stage)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print('Kurulum tamamlanamadı: ' + str(error), file=sys.stderr)
        sys.exit(1)
