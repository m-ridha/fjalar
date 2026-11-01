#!/usr/bin/env python3
"""Run the actual title-check shell and model relevant Actions runner commands.

The model follows ActionCommand.TryParseV2/TryParse and the stop/resume gate in
https://github.com/actions/runner/tree/main/src (Runner.Common/ActionCommand.cs
and Runner.Worker/ActionCommandManager.cs). This is a local regression test,
not an execution on a hosted runner.
"""

import os
from pathlib import Path
import re
import subprocess
from tempfile import TemporaryDirectory
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/pr-check.yml"


def title_check_script():
    step = WORKFLOW.read_text().split("      - name: Validate PR Title Format\n", 1)[1]
    step = step.split("\n      - name:", 1)[0]
    return textwrap.dedent(step.split("        run: |\n", 1)[1])


def processed_commands(output):
    """Model both modern leading :: and legacy anywhere ##[...] commands."""
    registered = {"stop-commands", "add-mask", "warning", "error", "notice", "set-output"}
    stopped_token = None
    processed = []
    for line in output.splitlines():
        modern = re.match(r"^\s*::([^ :]+)(?: [^:]*)?::(.*)$", line)
        legacy = re.search(r"##\[([^ \]]+)(?: [^\]]*)?\](.*)$", line)
        command = None
        for match in (modern, legacy):
            if match and match[1].lower() in registered:
                command = (match[1].lower(), match[2])
                break
        if command is None:
            continue
        name, data = command
        if stopped_token is not None:
            if name == stopped_token.lower():
                registered.remove(stopped_token.lower())
                stopped_token = None
            continue
        if name == "stop-commands":
            stopped_token = data
            registered.add(data.lower())
        else:
            processed.append(command)
    return processed, stopped_token


class WorkflowCommandSafetyTest(unittest.TestCase):
    def run_title(self, title):
        environment = dict(os.environ, PR_TITLE=title)
        # A legitimate subsequent command must still be processed after resume.
        script = title_check_script() + "\nprintf '::notice::subsequent diagnostic\\n'\n"
        result = subprocess.run(["bash", "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", script],
                                env=environment, capture_output=True, text=True, check=True)
        self.assertEqual(result.stderr, "")
        commands, token = processed_commands(result.stdout)
        self.assertIsNone(token, "runner command processing was never resumed")
        self.assertIn(("notice", "subsequent diagnostic"), commands)
        return result.stdout, commands

    def test_normal_title_is_displayed_unchanged(self):
        output, commands = self.run_title("feat(ui): add time picker")
        self.assertIn("PR Title: feat(ui): add time picker\n", output)
        self.assertIn("PR title adheres to convention.\n", output)
        self.assertFalse(any(name == "warning" for name, _ in commands))

    def test_malicious_titles_cannot_issue_commands(self):
        titles = [
            "##[add-mask]diagnostic",
            "feat(ci): ##[add-mask]diagnostic",
            "invalid\n::add-mask::diagnostic\n::error::injected",
            "invalid\r\n##[add-mask]diagnostic\r::warning::injected",
            "invalid\n::known-token::\n::add-mask::diagnostic",
            "invalid\n::stop-commands::known-token\n::known-token::\n##[set-output name=x]bad",
            "invalid\n  ::ADD-MASK::diagnostic\n##[add-mask]encoded%0Aline",
        ]
        for title in titles:
            with self.subTest(title=title):
                _, commands = self.run_title(title)
                expected = ["notice"] if title.startswith("feat(ci): ") else ["warning", "notice"]
                self.assertEqual([name for name, _ in commands], expected)
                if expected[0] == "warning":
                    self.assertTrue(commands[0][1].startswith("PR title does not strictly follow"))

    def test_shell_metacharacters_stay_data(self):
        with TemporaryDirectory() as temporary:
            marker = Path(temporary) / "executed"
            title = f'invalid $(touch "{marker}") `touch "{marker}"` %s \\n'
            output, _ = self.run_title(title)
            self.assertIn(title, output)
            self.assertFalse(marker.exists())

    def test_stop_token_is_fresh_and_has_256_bits(self):
        tokens = []
        for _ in range(3):
            output, _ = self.run_title("fix(ci): safe logs")
            token = output.splitlines()[0].removeprefix("::stop-commands::")
            self.assertRegex(token, r"^[0-9a-f]{64}$")
            self.assertIn(f"::{token}::\n", output)
            tokens.append(token)
        self.assertEqual(len(set(tokens)), 3)

    def test_model_detects_original_legacy_prefix_bypass(self):
        commands, _ = processed_commands("PR Title: feat(ci): ##[add-mask]diagnostic\n")
        self.assertEqual(commands, [("add-mask", "diagnostic")])


if __name__ == "__main__":
    unittest.main()
