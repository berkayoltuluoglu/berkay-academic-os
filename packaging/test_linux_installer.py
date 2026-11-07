"""Data-preservation and package-integrity checks without touching the real desktop."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('linux_installer', Path(__file__).with_name('install_linux.py'))
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class LinuxInstallerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='academic-os-installer-test-')
        self.root = Path(self.temporary.name)
        self.previous_umask = os.umask(0o077)

    def tearDown(self):
        os.umask(self.previous_umask)
        self.temporary.cleanup()

    def source_profile(self):
        source = self.root / 'source'
        files = {'data/tasks.json': '[{"id":"report","title":"Rapor"}]',
                 'notes/özel not.txt': 'Çalışma notu',
                 'config/google-calendar-tokens/all-calendars-v2/StoredCredential': 'private-local-token',
                 'config/profile-location.properties': 'dataRoot=/old/development/path',
                 'data/tasks.json.lock': '', 'data/cache/temporary.txt': 'cache'}
        for name, contents in files.items():
            file = source / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(contents)
        return source

    def package(self):
        bundle = self.root / 'package'
        files = {'academic-os': '#!/bin/sh\nexit 0\n', 'runtime/bin/java': 'test-runtime',
                 'academic-os.svg': '<svg/>', 'install_linux.py': '# test installer\n',
                 'defaults/scripts/test_script.py': 'print("test")\n'}
        for name, contents in files.items():
            target = bundle / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(contents)
        (bundle / 'academic-os').chmod(0o755)
        manifest = {'application': 'Berkay Academic OS', 'version': '1.4.1', 'platform': 'linux-x64',
                    'files': {name: installer.sha256(bundle / name) for name in files}}
        (bundle / 'package-manifest.json').write_text(json.dumps(manifest))
        return bundle

    def test_import_preserves_original_and_local_sign_in_but_backup_excludes_tokens(self):
        source = self.source_profile()
        before = {str(p): p.read_bytes() for p in source.rglob('*') if p.is_file()}
        destination = self.root / 'profile'
        installer.import_profile(source, destination)
        for name, content in before.items():
            self.assertEqual(content, Path(name).read_bytes())
        self.assertEqual((source / 'data/tasks.json').read_bytes(), (destination / 'data/tasks.json').read_bytes())
        token = destination / 'config/google-calendar-tokens/all-calendars-v2/StoredCredential'
        self.assertEqual('private-local-token', token.read_text())
        self.assertEqual(0o600, token.stat().st_mode & 0o777)
        self.assertFalse((destination / 'config/profile-location.properties').exists())
        self.assertFalse((destination / 'data/tasks.json.lock').exists())
        self.assertFalse((destination / 'data/cache/temporary.txt').exists())
        with zipfile.ZipFile(next((destination / 'backups').glob('*.zip'))) as backup:
            self.assertNotIn(token.relative_to(destination).as_posix(), backup.namelist())
            manifest = json.loads(backup.read('manifest.json'))
            for entry in manifest['files']:
                self.assertEqual(entry['sha256'], installer.sha256(destination / entry['path']))

    def test_import_refuses_existing_profile(self):
        source = self.source_profile()
        target = self.root / 'existing'
        target.mkdir()
        (target / 'keep.txt').write_text('keep')
        with self.assertRaisesRegex(RuntimeError, 'zaten var'):
            installer.import_profile(source, target)
        self.assertEqual('keep', (target / 'keep.txt').read_text())

    def test_running_source_is_not_copied(self):
        source = self.source_profile()
        code = ('import fcntl,sys; f=open(sys.argv[1],"a+b"); '
                'fcntl.lockf(f,fcntl.LOCK_EX); print("ready",flush=True); sys.stdin.read()')
        with subprocess.Popen([sys.executable, '-c', code, str(source / '.instance.lock')],
                              stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True) as running:
            try:
                self.assertEqual('ready', running.stdout.readline().strip())
                with self.assertRaisesRegex(RuntimeError, 'açık'):
                    installer.import_profile(source, self.root / 'destination')
                self.assertFalse((self.root / 'destination').exists())
            finally:
                running.stdin.close()
                running.wait(timeout=5)

    def test_tampered_package_is_rejected_before_install(self):
        bundle = self.package()
        installer.verify_package(bundle)
        (bundle / 'runtime/bin/java').write_text('modified')
        with self.assertRaisesRegex(RuntimeError, 'doğrulaması başarısız'):
            installer.verify_package(bundle)

    def test_reinstall_keeps_user_records_and_uses_absolute_desktop_launcher(self):
        bundle = self.package()
        data = self.root / 'user-data'
        home = self.root / 'home'
        argv = ['install_linux.py', '--package', str(bundle), '--prefix', str(self.root / 'app path'), '--no-desktop']
        environment = {'HOME': str(home), 'XDG_DATA_HOME': str(data), 'XDG_STATE_HOME': str(self.root / 'state')}
        with patch.dict(os.environ, environment), patch.object(sys, 'argv', argv), contextlib.redirect_stdout(io.StringIO()):
            installer.main()
            record = data / 'berkay-academic-os/data/tasks.json'
            record.parent.mkdir()
            record.write_text('user-edited-record')
            installer.main()
        self.assertEqual('user-edited-record', record.read_text())
        entry = data / 'applications/berkay-academic-os.desktop'
        self.assertIn('app path/current/academic-os"', entry.read_text())
        self.assertIn('Terminal=false', entry.read_text())
        self.assertTrue((self.root / 'app path/current/runtime/bin/java').is_file())


if __name__ == '__main__':
    unittest.main()
