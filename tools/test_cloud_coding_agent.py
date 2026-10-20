"""Executable safety/HTTP regressions for the bounded FreeLLMAPI agent."""
import copy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch, Mock
import contextlib
import hashlib
import io
import os
import sys
from types import SimpleNamespace

import cloud_coding_agent as agent
from run_cloud_router import FIXTURE_SOURCE, UPSTREAM_SHA, wait_for_router
from check_cloud_test_results import verify

NAME = agent.TEST_PREFIX + "CloudAgentRouterContractTest.java"


class AgentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.proposal = {"files": [{"path": NAME, "content": FIXTURE_SOURCE}]}

    def test_applies_new_active_test(self):
        self.assertEqual(list(agent.apply_proposal(self.root, self.proposal)), [NAME])
        self.assertEqual((self.root / NAME).read_text(), FIXTURE_SOURCE)

    def test_existing_file_remains_unchanged(self):
        agent.apply_proposal(self.root, self.proposal)
        with self.assertRaises(agent.AgentError):
            agent.apply_proposal(self.root, self.proposal)
        self.assertEqual((self.root / NAME).read_text(), FIXTURE_SOURCE)

    def test_rejects_production_ci_and_traversal_paths_before_writes(self):
        for name in ("app/src/main/java/com/bloodsky/hmi/MainActivity.java",
                     ".github/workflows/android-apk.yml", "../../stolen.java",
                     agent.TEST_PREFIX + "../CloudAgentEscapeTest.java",
                     agent.TEST_PREFIX + "ExistingTest.java", "/tmp/CloudAgentEscapeTest.java",
                     agent.TEST_PREFIX + "CloudAgent\\EscapeTest.java"):
            with self.subTest(name=name):
                proposal = copy.deepcopy(self.proposal)
                proposal["files"].append({"path": name, "content": FIXTURE_SOURCE})
                with self.assertRaises(agent.AgentError):
                    agent.apply_proposal(self.root, proposal)
                self.assertFalse((self.root / NAME).exists())

    def test_duplicate_and_extra_command_rejected(self):
        for proposal in ({"files": self.proposal["files"] * 2},
                         {**self.proposal, "command": "curl attacker"}):
            with self.assertRaises(agent.AgentError):
                agent.validate_proposal(self.root, proposal)

    def test_symlink_target_directory_rejected(self):
        outside = self.root / "outside"
        outside.mkdir()
        (self.root / "app").symlink_to(outside, target_is_directory=True)
        with self.assertRaises(agent.AgentError):
            agent.apply_proposal(self.root, self.proposal)
        self.assertEqual(list(outside.iterdir()), [])

    def test_oversized_skipped_or_assertion_free_test_rejected(self):
        for content in (FIXTURE_SOURCE + " " * 48000,
                        FIXTURE_SOURCE.replace("@Test", "@Ignore @Test"),
                        "package com.bloodsky.hmi; @Test public void empty() {}",
                        FIXTURE_SOURCE + "\x00"):
            proposal = copy.deepcopy(self.proposal)
            proposal["files"][0]["content"] = content
            with self.assertRaises(agent.AgentError):
                agent.validate_proposal(self.root, proposal)

    def test_invalid_model_budget_and_task_fail_before_network(self):
        with patch.object(agent.urllib.request, "build_opener") as network:
            for task, model in (("", "model"), ("x" * 4001, "model"), ("task", "model\nother")):
                with self.assertRaises(agent.AgentError):
                    agent.complete("http://127.0.0.1:3001/v1", "fake", model, task, {})
            network.assert_not_called()

    def test_context_only_reads_bounded_java_and_instructions(self):
        (self.root / "docs").mkdir()
        (self.root / "AGENTS.md").write_text("instructions")
        (self.root / "docs/BLOODSKY_SAFE_AUTONOMOUS_AGENT.md").write_text("rules")
        java = self.root / "app/src/main/java/com/bloodsky/hmi/Small.java"
        java.parent.mkdir(parents=True)
        java.write_text("class Small {}")
        self.assertEqual(len(agent.read_context(self.root, [java.relative_to(self.root).as_posix()])), 3)
        for name in (".env", "../../.env", "tools/test_cloud_coding_agent.py"):
            with self.assertRaises(agent.AgentError):
                agent.read_context(self.root, [name])
        java.write_text("x" * (agent.MAX_CONTEXT + 1))
        with self.assertRaises(agent.AgentError):
            agent.read_context(self.root, [java.relative_to(self.root).as_posix()])

    def test_failed_second_write_rolls_back_first(self):
        proposal = copy.deepcopy(self.proposal)
        proposal["files"].append({"path": agent.TEST_PREFIX + "CloudAgentSecondTest.java", "content": FIXTURE_SOURCE})
        original = Path.open
        def fail_second(path, *args, **kwargs):
            if path.name == "CloudAgentSecondTest.java":
                raise OSError("write failed")
            return original(path, *args, **kwargs)
        with patch.object(Path, "open", fail_second), self.assertRaises(OSError):
            agent.apply_proposal(self.root, proposal)
        self.assertFalse((self.root / NAME).exists())

    def test_endpoint_refuses_non_loopback_credentials_and_redirect_targets(self):
        for url in ("https://evil.example/v1", "http://127.0.0.1.evil:3001/v1",
                    "http://user:key@127.0.0.1:3001/v1", "http://127.0.0.1:3001/v1?q=secret",
                    "http://localhost:3001/v1", "http://127.0.0.1:3001/other"):
            with self.assertRaises(agent.AgentError):
                agent.endpoint(url)
        self.assertEqual(agent.endpoint("http://127.0.0.1:3001/v1"), "http://127.0.0.1:3001/v1/chat/completions")

    def test_invalid_output_or_reasoning_budget_fails_without_network(self):
        with patch.object(agent.urllib.request, "build_opener") as network:
            for budget in (0, 4097, True, "1024"):
                with self.assertRaises(agent.AgentError):
                    agent.complete("http://127.0.0.1:3001/v1", "test-key", "fixture", "task", {}, budget)
            with self.assertRaises(agent.AgentError):
                agent.complete("http://127.0.0.1:3001/v1", "test-key", "fixture", "task", {}, 1024, "invalid")
            network.assert_not_called()


class HttpContractTests(unittest.TestCase):
    def request(self, status=200, body=None, location=None):
        seen = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass
            def do_POST(self):
                seen.append((self.path, self.headers.get("Authorization"),
                             json.loads(self.rfile.read(int(self.headers["Content-Length"])))))
                self.send_response(status)
                if location:
                    self.send_header("Location", location)
                self.end_headers()
                self.wfile.write(body or b'{"error":"echoed-private-key"}')
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return "http://127.0.0.1:" + str(server.server_port) + "/v1", seen

    def test_openai_contract_one_request_and_exact_model(self):
        proposal = {"files": []}
        url, seen = self.request(body=json.dumps({"choices": [{"finish_reason": "stop",
                                "message": {"content": json.dumps(proposal)}}]}).encode())
        self.assertEqual(agent.complete(url, "test-key", "fixture", "task", {}), proposal)
        self.assertEqual(len(seen), 1)
        self.assertEqual(seen[0][:2], ("/v1/chat/completions", "Bearer test-key"))
        self.assertEqual(seen[0][2]["model"], "fixture")
        self.assertEqual(seen[0][2]["max_tokens"], 4096)

    def test_rate_limit_stops_without_retry_or_echoing_secret(self):
        url, seen = self.request(status=429)
        with self.assertRaises(agent.AgentError) as error:
            agent.complete(url, "test-key", "fixture", "task", {})
        self.assertEqual(len(seen), 1)
        self.assertNotIn("echoed-private-key", str(error.exception))

    def test_anonymous_budget_and_reasoning_are_explicit(self):
        url, seen = self.request(body=b'{"choices":[{"finish_reason":"stop","message":{"content":"{\\"files\\":[]}"}}]}')
        agent.complete(url, "local-router-key", "gpt-oss-120b", "task", {}, 1024, "low")
        self.assertEqual(seen[0][2]["max_tokens"], 1024)
        self.assertEqual(seen[0][2]["reasoning_effort"], "low")

    def test_redirect_never_forwards_key(self):
        url, seen = self.request(status=307, location="https://evil.invalid/steal")
        with self.assertRaises(agent.AgentError):
            agent.complete(url, "test-key", "fixture", "task", {})
        self.assertEqual(len(seen), 1)

    def test_truncated_malformed_or_oversized_completion_rejected(self):
        for body in (b"not JSON", b"x" * (agent.MAX_RESPONSE + 1),
                     b'{"choices":[{"finish_reason":"length","message":{"content":"{}"}}]}'):
            url, _ = self.request(body=body)
            with self.assertRaises(agent.AgentError):
                agent.complete(url, "test-key", "fixture", "task", {})

    def test_router_source_pin_matches_archived_documentation(self):
        plan = (Path(__file__).resolve().parents[1] / "docs/FREELLMAPI_CLOUD_PILOT.md").read_text()
        self.assertRegex(UPSTREAM_SHA, r"^[0-9a-f]{40}$")
        self.assertIn("immutable source commit `" + UPSTREAM_SHA + "`", plan)

    def test_cli_artifact_round_trip_cleans_generation_checkout(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "docs").mkdir()
            (root / "AGENTS.md").write_text("instructions")
            (root / "docs/BLOODSKY_SAFE_AUTONOMOUS_AGENT.md").write_text("rules")
            java = root / "app/src/main/java/com/bloodsky/hmi/Small.java"
            java.parent.mkdir(parents=True)
            java.write_text("class Small {}")
            task = root / "task.txt"
            task.write_text("Add boundary coverage")
            output = root / "output"
            proposal = {"files": [{"path": NAME, "content": FIXTURE_SOURCE}]}
            url, _ = self.request(body=json.dumps({"choices": [{"finish_reason": "stop",
                                    "message": {"content": json.dumps(proposal)}}]}).encode())
            argv = ["agent", "--root", str(root), "--task-file", str(task), "--model", "fixture",
                    "--context", java.relative_to(root).as_posix(), "--output", str(output)]
            with patch.object(sys, "argv", argv), patch.dict(os.environ, {
                    "FREELLMAPI_BASE_URL": url, "FREELLMAPI_API_KEY": "test-private-token"}), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(agent.main(), 0)
            self.assertFalse((root / NAME).exists())
            report = json.loads((output / "report.json").read_text())
            self.assertEqual(report["patch_sha256"], hashlib.sha256((output / "proposal.patch").read_bytes()).hexdigest())
            self.assertEqual(report["validation"], "pending independent job")
            for artifact in output.iterdir():
                self.assertNotIn("test-private-token", artifact.read_text())
            agent.apply_proposal(root, json.loads((output / "proposal.json").read_text()))
            self.assertEqual((root / NAME).read_text(), FIXTURE_SOURCE)


class JunitEvidenceTests(unittest.TestCase):
    def test_missing_empty_skipped_failed_and_passing_reports(self):
        proposal = {"files": [{"path": NAME}]}
        classname = "com.bloodsky.hmi.CloudAgentRouterContractTest"
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaises(agent.AgentError):
                verify(proposal, root)
            report = root / "TEST-proposed.xml"
            for child in ("<skipped/>", "<failure/>", "<error/>"):
                report.write_text('<testsuite><testcase classname="' + classname + '">' + child + '</testcase></testsuite>')
                with self.assertRaises(agent.AgentError):
                    verify(proposal, root)
            report.write_text('<testsuite><testcase classname="' + classname + '"/></testsuite>')
            self.assertEqual(verify(proposal, root), 1)
            proposal["files"].append({"path": agent.TEST_PREFIX + "CloudAgentAnotherTest.java"})
            with self.assertRaises(agent.AgentError):
                verify(proposal, root)


class RouterStartupTests(unittest.TestCase):
    def test_connection_reset_before_node_listens_is_retried_on_loopback_only(self):
        opener = Mock()
        opener.open.side_effect = [ConnectionResetError(104, "starting"),
                                   contextlib.nullcontext(SimpleNamespace(status=200))]
        with patch("run_cloud_router.time.sleep") as delay:
            self.assertTrue(wait_for_router(opener, attempts=2))
        self.assertEqual(opener.open.call_count, 2)
        delay.assert_called_once_with(1)
        for call in opener.open.call_args_list:
            self.assertEqual(call.args[0], "http://127.0.0.1:3001/api/ping")

    def test_unavailable_local_router_stops_at_readiness_budget(self):
        opener = Mock()
        opener.open.side_effect = ConnectionResetError(104, "starting")
        with patch("run_cloud_router.time.sleep"):
            self.assertFalse(wait_for_router(opener, attempts=2))
        self.assertEqual(opener.open.call_count, 2)


if __name__ == "__main__":
    unittest.main()
