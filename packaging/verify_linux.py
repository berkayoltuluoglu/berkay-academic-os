#!/usr/bin/env python3
"""Verify a desktop package in an isolated profile, optionally install and open it."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

from install_linux import verify_package


def smoke_test(launcher: Path, profile: Path, environment: dict) -> None:
    with subprocess.Popen([str(launcher), '--smoke-test', str(profile)], cwd='/', env=environment) as process:
        try:
            # A tiling WM can override JavaFX's requested size. Float only the temporary
            # verification window so its full-size screenshot checks the same layout as Windows.
            if shutil.which('i3-msg') and shutil.which('xdotool'):
                for _ in range(50):
                    if process.poll() is not None:
                        break
                    found = subprocess.run(['xdotool', 'search', '--onlyvisible', '--name', '^Academic OS Package Verification$'],
                                           capture_output=True, text=True)
                    if found.returncode == 0:
                        window = found.stdout.splitlines()[0]
                        result = subprocess.run(['i3-msg', f'[id="{window}"] floating enable, resize set width 1400 px height 900 px'],
                                                capture_output=True, text=True)
                        if result.returncode == 0:
                            print('Temporary verification window sized for the tiling desktop.', flush=True)
                        break
                    time.sleep(0.1)
            process.wait(timeout=40)
            if process.returncode:
                raise RuntimeError('Paketli JavaFX işlemi başarısız oldu.')
        except BaseException:
            process.kill()
            process.wait()
            raise


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=Path('/usr/lib/jvm/java-21-openjdk-amd64'))
    parser.add_argument('--gui', action='store_true')
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--import-profile', type=Path)
    args = parser.parse_args()
    package = args.package.resolve()
    verify_package(package)
    with tempfile.TemporaryDirectory(prefix='academic-os-linux-check-') as temporary:
        root = Path(temporary)
        source = root / 'source'
        (source / 'data').mkdir(parents=True)
        (source / 'notes').mkdir()
        (source / 'config').mkdir()
        task = {'id': 'linux-check', 'title': 'Raporu hazırla', 'date': '2026-10-23',
                'description': '', 'status': 'Bekliyor', 'priority': 'Normal', 'subtasks': []}
        (source / 'data/tasks.json').write_text(json.dumps([task]))
        (source / 'notes/çalışma.txt').write_text('Notlar korunur.')
        (source / 'config/local-token-test').write_text('never-in-the-distribution')
        environment = dict(os.environ, HOME=str(root / 'home'), XDG_DATA_HOME=str(root / 'user data'),
                           XDG_CONFIG_HOME=str(root / 'configuration'), XDG_STATE_HOME=str(root / 'state'))
        install = [sys.executable, str(package / 'install_linux.py'), '--prefix', str(root / 'application with spaces'), '--no-desktop']
        subprocess.run(install + ['--import-profile', str(source)], env=environment, check=True)
        profile = root / 'user data/berkay-academic-os'
        assert (profile / 'data/tasks.json').read_bytes() == (source / 'data/tasks.json').read_bytes()
        assert (profile / 'config/local-token-test').read_text() == 'never-in-the-distribution'
        backup = next((profile / 'backups').glob('*.zip'))
        with zipfile.ZipFile(backup) as archive:
            assert not any(name.startswith('config/') for name in archive.namelist())
        launcher = root / 'application with spaces/current/academic-os'
        subprocess.run([str(launcher), '--self-check'], cwd='/', env=environment, check=True)
        cli = root / 'home/.local/bin/academic-os'
        if not cli.is_file():
            raise RuntimeError('CLI PATH başlatıcısı kurulmadı.')
        result_cli = subprocess.run([str(cli), '--profile', str(root / 'CLI profile with spaces'), 'snapshot', '--week', '2026-11-09'],
                                    cwd='/', env=environment, capture_output=True, text=True, check=True)
        if json.loads(result_cli.stdout)['week'] != '2026-11-09':
            raise RuntimeError('Paketli CLI çıktısı geçersiz.')
        if not (profile / 'AGENTS.md').is_file() or not (profile / 'CLI.md').is_file():
            raise RuntimeError('Profilde ajan kılavuzu eksik.')
        desktop = root / 'user data/applications/berkay-academic-os.desktop'
        if shutil.which('desktop-file-validate'):
            subprocess.run(['desktop-file-validate', str(desktop)], check=True)
        # Validate the Python-produced backup with the actual Java application's reader.
        helper = root / 'VerifyLinuxProfile.java'
        helper.write_text('''import org.example.*;
import java.nio.file.*;
class VerifyLinuxProfile {
    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]);
        var preview = new BackupService(root).inspect(Path.of(args[1]));
        if (preview.files() != 2) throw new IllegalStateException("Incomplete backup");
        if (new TaskStorageService(root.resolve("data/tasks.json")).loadTasks().size() != 1)
            throw new IllegalStateException("Task migration failed");
        var config = new ConfigLoader().load(root);
        if (config.getModules().stream().filter(m -> m.getId().equals("uart") || m.getId().equals("scripts")).anyMatch(m -> !m.isEnabled()))
            throw new IllegalStateException("Linux tools were disabled");
        System.out.println("Java profile and backup verification OK");
    }
}
''')
        subprocess.run([str(args.java_home / 'bin/java'), '-Dacademic.os.packaged=true', '-cp', str(package / 'app/lib/*'),
                        str(helper), str(profile), str(backup)], env=environment, cwd=root, check=True)
        (profile / 'notes/çalışma.txt').write_text('User edit after installation')
        subprocess.run(install, env=environment, check=True)
        assert (profile / 'notes/çalışma.txt').read_text() == 'User edit after installation'
        if args.gui:
            smoke = root / 'empty GUI profile'
            smoke_test(launcher, smoke, environment)
            if not (smoke / 'package-smoke.ok').is_file():
                raise RuntimeError('JavaFX penceresi çizilemedi.')
            repository = Path(__file__).resolve().parents[1]
            version = json.loads((package / 'package-manifest.json').read_text())['version']
            report = repository / f'app/build/reports/linux-package-{version}.png'
            shutil.copyfile(smoke / 'package-smoke.png', report)
            print('Bundled JavaFX desktop verification OK: ' + str(report))
    print('Linux package, profile import, backup and reinstall verification OK', flush=True)
    if args.install:
        command = [sys.executable, str(package / 'install_linux.py')]
        if args.import_profile:
            command += ['--import-profile', str(args.import_profile.resolve())]
        subprocess.run(command, check=True)
        if args.gui:
            subprocess.run(['gtk-launch', 'berkay-academic-os'], check=True)
            if shutil.which('xdotool') and shutil.which('xprop'):
                for _ in range(50):
                    windows = subprocess.run(['xdotool', 'search', '--onlyvisible', '--name', '^Berkay Academic OS$'], capture_output=True, text=True)
                    if windows.returncode == 0:
                        window = windows.stdout.splitlines()[0]
                        subprocess.run(['xprop', '-id', window, 'WM_CLASS', '_NET_WM_PID'], check=True)
                        print('Installed application opened from its desktop entry.')
                        break
                    time.sleep(0.2)
                else:
                    raise RuntimeError('Kurulum tamamlandı ancak uygulama penceresi doğrulanamadı; application.log dosyasını incele.')


if __name__ == '__main__':
    main()
