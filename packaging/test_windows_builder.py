"""Windows payload checks using temporary fixtures, without a desktop or Google account."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('windows_builder', Path(__file__).with_name('build_windows.py'))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class WindowsBuilderTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='academic-windows-build-')
        self.root = Path(self.temporary.name)
        self.repo = self.root / 'source'
        self.build = self.root / 'windows'
        self.payload = self.build / 'payload-1.5.1'
        fixtures = {
            'app/src/main/resources/config.json': json.dumps({'app': {'version': '1.5.1'}}),
            'packaging/academic-os.cmd': '@echo off\nrem CLI\n',
            'packaging/WINDOWS.md': 'Windows ve ajan kılavuzu',
            'app/src/main/resources/agent-guide/AGENTS.md': 'Planlama kuralları',
            'app/src/main/resources/agent-guide/CLAUDE.md': 'AGENTS.md oku',
            'app/src/main/resources/agent-guide/CLI.md': 'CLI başvurusu',
            'app/src/main/resources/agent-guide/planner-proposal.json': '{}',
            'data/tasks.json': 'PERSONAL_PROFILE_MUST_NOT_BE_PACKAGED',
        }
        for name, value in fixtures.items():
            file = self.repo / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(value, encoding='utf-8')
        self.libraries = self.build / 'input/lib'
        self.libraries.mkdir(parents=True)
        for name in ('app-1.5.1.jar', 'javafx-graphics-21.0.2-win.jar'):
            (self.libraries / name).write_bytes(b'jar fixture')
        self.archive = self.root / 'runtime.zip'
        with zipfile.ZipFile(self.archive, 'w') as archive:
            archive.writestr('jre/bin/javaw.exe', b'MZ runtime fixture')
            archive.writestr('jre/bin/java.exe', b'MZ runtime fixture')

    def tearDown(self):
        self.temporary.cleanup()

    def build_payload(self):
        commands = []
        def compile_nsis(command, **kwargs):
            commands.append(command)
            output = next(value.split('=', 1)[1] for value in command if value.startswith(('-DOUTPUT=', '/DOUTPUT=')))
            Path(output).write_bytes(b'MZ compiler fixture')

        with patch.object(builder, 'REPOSITORY', self.repo), patch.object(builder, 'BUILD', self.build), \
                patch.object(builder, 'runtime', return_value=(self.archive, {'version': '21', 'sha256': builder.sha256(self.archive)})), \
                patch.object(builder.subprocess, 'run', side_effect=compile_nsis), \
                patch('sys.argv', ['build_windows.py', '--skip-gradle', '--makensis', 'mock-makensis']):
            builder.main()
        return commands

    def test_clean_build_contains_cli_guides_and_complete_file_hashes(self):
        self.build_payload()
        payload = self.payload
        for name in ('academic-os.cmd', 'WINDOWS.md', 'agent-guide/AGENTS.md', 'agent-guide/CLI.md',
                     'BerkayAcademicOS.exe', 'runtime/bin/javaw.exe', 'app/lib/app-1.5.1.jar'):
            self.assertTrue((payload / name).is_file(), name)
        manifest = json.loads((payload / 'package-manifest.json').read_text())
        self.assertFalse(manifest['googleClientBundled'])
        self.assertFalse((payload / 'data').exists())
        self.assertEqual(set(manifest['files']), {file.relative_to(payload).as_posix() for file in payload.rglob('*')
                                                if file.is_file() and file.name != 'package-manifest.json'})
        for name, digest in manifest['files'].items():
            self.assertEqual(digest, builder.sha256(payload / name), name)
        setup = self.build / 'dist/BerkayAcademicOS-1.5.1-setup.exe'
        self.assertTrue(setup.is_file())
        self.assertTrue(setup.with_name(setup.name + '.sha256').read_text().startswith(builder.sha256(setup)))

    def test_rebuild_removes_only_stale_payload_files(self):
        self.build_payload()
        (self.payload / 'stale.jar').write_bytes(b'old')
        self.build_payload()
        self.assertFalse((self.payload / 'stale.jar').exists())
        self.assertEqual('PERSONAL_PROFILE_MUST_NOT_BE_PACKAGED', (self.repo / 'data/tasks.json').read_text())

    def test_old_application_jar_is_rejected_before_replacing_payload(self):
        (self.libraries / 'app-1.4.1.jar').write_bytes(b'old')
        with self.assertRaisesRegex(RuntimeError, 'release version'):
            self.build_payload()
        self.assertFalse(self.payload.exists())

    def test_previous_release_artifacts_are_preserved(self):
        old = self.build / 'payload/keep-previous-artifact.txt'
        old.parent.mkdir(parents=True)
        old.write_text('keep')
        self.build_payload()
        self.assertEqual('keep', old.read_text())

    def test_linux_native_library_cannot_enter_windows_payload(self):
        (self.libraries / 'javafx-base-21.0.2-linux.jar').write_bytes(b'wrong platform')
        with self.assertRaisesRegex(RuntimeError, 'non-Windows'):
            self.build_payload()

    def test_runtime_archive_cannot_escape_destination(self):
        with zipfile.ZipFile(self.archive, 'w') as archive:
            archive.writestr('jre/../../escape', b'bad')
        with self.assertRaisesRegex(RuntimeError, 'Unsafe runtime'):
            builder.extract_runtime(self.archive, self.root / 'runtime')
        self.assertFalse((self.root / 'escape').exists())

    def test_approved_icon_is_passed_to_launcher_and_setup_compilers(self):
        icon = self.repo / 'app/src/main/resources/branding/app-icon.ico'
        icon.parent.mkdir(parents=True)
        icon.write_bytes(b'icon fixture')
        commands = self.build_payload()
        self.assertEqual(2, len(commands))
        for command in commands:
            self.assertTrue(any(value.endswith('APP_ICON=' + str(icon)) for value in command), command)
        manifest = json.loads((self.payload / 'package-manifest.json').read_text())
        self.assertTrue(manifest['customIconBundled'])


if __name__ == '__main__':
    unittest.main()
