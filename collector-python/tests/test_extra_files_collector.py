"""Mirror of `collector-java/.../src/test/kotlin/action/ExtraFilesCollectorTests.kt`."""

from _support import CollectorTestCase, dic


class ExtraFilesCollectorTests(CollectorTestCase):

    def setUp(self):
        super().setUp()
        self.init_context()  # Java: Context.init(projectRootDir, buildDir, DeployType.GAE)

    def extra_uuid_map(self):
        return dic.Context.get().extra_uuid_map

    def test_copies_files(self):
        contents = {"file01": "one", "file02": "two", "file03": "three"}
        for name, content in contents.items():
            (self.project_root_dir / name).write_text(content)

        dic.collect_extra_files(list(contents))

        extra_uuid_map = self.extra_uuid_map()
        self.assertEqual(3, len(extra_uuid_map))
        for uuid_key in extra_uuid_map:
            self.assertTrue((dic.Context.get().output_dir / uuid_key).is_file())
        self.assertTrue(all(value.startswith("/") for value in extra_uuid_map.values()))
        # The map keys are the normalized "/name" paths and the copies are verbatim.
        for value in extra_uuid_map.values():
            source = self.project_root_dir / value.removeprefix("/")
            copied = next(
                dic.Context.get().output_dir / uuid_key
                for uuid_key, mapped in extra_uuid_map.items()
                if mapped == value
            )
            self.assertEqual(source.read_text(), copied.read_text())

    def test_copies_files_but_ignores_duplicate_entries(self):
        (self.project_root_dir / "file01").write_text("one")

        dic.collect_extra_files(["file01", "file01"])

        self.assertEqual(1, len(self.extra_uuid_map()))

    def test_copies_files_but_ignores_binary_files(self):
        (self.project_root_dir / "binaryFile.bin").write_bytes(b"binary file")
        (self.project_root_dir / "textFile.txt").write_text("text file")

        dic.collect_extra_files(["binaryFile.bin", "textFile.txt"])

        extra_uuid_map = self.extra_uuid_map()
        self.assertEqual(1, len(extra_uuid_map))
        self.assertTrue(next(iter(extra_uuid_map.values())).endswith("textFile.txt"))

    def test_copies_files_but_ignores_files_that_do_not_exist(self):
        (self.project_root_dir / "file01").write_text("one")

        dic.collect_extra_files(["file01", "aFileThatDoesNotExist"])

        extra_uuid_map = self.extra_uuid_map()
        self.assertEqual(1, len(extra_uuid_map))
        self.assertTrue(next(iter(extra_uuid_map.values())).endswith("file01"))
        # Invalid entries are announced on the error log.
        self.assertIn("aFileThatDoesNotExist", self.stderr_capture.getvalue())


if __name__ == "__main__":
    unittest.main()
