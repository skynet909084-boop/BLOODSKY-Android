#!/usr/bin/env python3
"""Start an ephemeral pinned FreeLLMAPI router and run the bounded agent.

Fixture mode uses an internal Docker network: no provider credentials or
outbound internet. Live mode uses only one explicitly selected Groq free model.
Never upload router logs, its database, generated keys or startup configuration.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

UPSTREAM_SHA = "7ae0ef193d297c02479efed2a07946c44457512b"  # v0.15.0, MIT
LIVE_MODEL = "openai/gpt-oss-120b"
FIXTURE_MODEL = "bloodsky-fixture"
FIXTURE_SOURCE = '''package com.bloodsky.hmi;
import org.junit.Test;
import static org.junit.Assert.*;
public class CloudAgentRouterContractTest {
    @Test public void maximumTimerAndDecimalCommaRemainAccepted() {
        FbdPropertyEdits edit = FbdPropertyEdits.parse("3600000", null, "-1,25", "<>", "2,5");
        assertEquals(Integer.valueOf(3600000), edit.timer);
        assertEquals(-1.25, edit.left, 0.0);
        assertEquals("!=", edit.operator);
        assertEquals(2.5, edit.right, 0.0);
    }
    @Test(expected = IllegalArgumentException.class)
    public void zeroTimerRemainsRejected() {
        FbdPropertyEdits.parse("0", null, null, null, null);
    }
}
'''


class FixtureHandler(BaseHTTPRequestHandler):
    calls = 0

    def log_message(self, *_):
        pass

    def do_POST(self):
        size = int(self.headers.get("Content-Length", "0"))
        if self.path != "/v1/chat/completions" or not 0 < size <= 150000:
            self.send_error(400)
            return
        request = json.loads(self.rfile.read(size))
        if request.get("model") != FIXTURE_MODEL:
            self.send_error(400)
            return
        type(self).calls += 1
        proposal = {"files": [{"path": "app/src/test/java/com/bloodsky/hmi/CloudAgentRouterContractTest.java",
                               "content": FIXTURE_SOURCE}]}
        response = {"id": "contract", "object": "chat.completion", "created": 0,
                    "model": FIXTURE_MODEL, "choices": [{"index": 0, "finish_reason": "stop",
                    "message": {"role": "assistant", "content": json.dumps(proposal)}}],
                    "usage": {"prompt_tokens": 100, "completion_tokens": 200, "total_tokens": 300}}
        data = json.dumps(response).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def docker(*args, **kwargs):
    # Capture output because Docker/node diagnostics may include configuration.
    result = subprocess.run(["docker", *args], capture_output=True, text=True,
                            timeout=kwargs.pop("timeout", 60), **kwargs)
    if result.returncode:
        raise RuntimeError("Docker operation failed: " + args[0])
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture-server", action="store_true")
    parser.add_argument("--mode", choices=("fixture", "live"), default="fixture")
    parser.add_argument("--image", default="bloodsky-freellmapi:pilot")
    parser.add_argument("--task-file", type=Path)
    parser.add_argument("--context", nargs="+")
    parser.add_argument("--output", type=Path, default=Path("cloud-output"))
    args = parser.parse_args()
    if args.fixture_server:
        ThreadingHTTPServer(("0.0.0.0", 3002), FixtureHandler).serve_forever()
        return
    if args.mode == "live" and (os.environ.get("BLOODSKY_FREE_TIER_CONFIRMED") != "true"
                                or not os.environ.get("GROQ_FREE_API_KEY")):
        raise RuntimeError("Live mode requires confirmed free-only Groq account and GROQ_FREE_API_KEY")
    suffix = secrets.token_hex(6)
    router, fixture, network = "bloodsky-router-" + suffix, "bloodsky-fixture-" + suffix, "bloodsky-" + suffix
    root = Path(__file__).resolve().parents[1]
    model = FIXTURE_MODEL if args.mode == "fixture" else LIVE_MODEL
    with tempfile.TemporaryDirectory(prefix="bloodsky-router-") as temp:
        work = Path(temp)
        config = {"admin": {"email": "pilot@example.invalid", "password": secrets.token_hex(24)},
                  "routing": {"strategy": "priority"}}
        if args.mode == "fixture":
            config["customProviders"] = [{"baseUrl": "http://" + fixture + ":3002/v1",
                                           "models": [{"model": model, "supportsTools": False}]}]
            config["fallback"] = [{"platform": "custom", "modelId": model, "priority": 1}]
        else:
            config["keys"] = [{"platform": "groq", "key": os.environ["GROQ_FREE_API_KEY"]}]
            config["fallback"] = [{"platform": "groq", "modelId": model, "priority": 1}]
        env_file = work / "router.env"
        env_file.write_text("ENCRYPTION_KEY=" + secrets.token_hex(32)
                            + "\nNODE_ENV=production\nCATALOG_SYNC_DISABLED=1\n"
                            + "FREEAPI_CONFIG_JSON=" + json.dumps(config) + "\n", encoding="utf-8")
        env_file.chmod(0o600)
        try:
            docker("network", "create", *(["--internal"] if args.mode == "fixture" else []), network)
            if args.mode == "fixture":
                # Reuse Node from the pinned router build; no extra image/tag.
                proposal = {"files": [{"path": "app/src/test/java/com/bloodsky/hmi/CloudAgentRouterContractTest.java",
                                       "content": FIXTURE_SOURCE}]}
                response = {"id": "contract", "object": "chat.completion", "created": 0,
                            "model": model, "choices": [{"index": 0, "finish_reason": "stop",
                            "message": {"role": "assistant", "content": json.dumps(proposal)}}],
                            "usage": {"prompt_tokens": 100, "completion_tokens": 200, "total_tokens": 300}}
                server = ("const http=require('http'); const body=" + json.dumps(json.dumps(response)) + ";"
                          "http.createServer((q,s)=>{q.resume();q.on('end',()=>{"
                          "s.writeHead(200,{'Content-Type':'application/json'});s.end(body);});}).listen(3002,'0.0.0.0');")
                docker("run", "-d", "--name", fixture, "--network", network,
                       "--entrypoint", "node", args.image, "-e", server)
            docker("run", "-d", "--name", router, "--network", network,
                   "-p", "127.0.0.1:3001:3001", "--env-file", str(env_file), args.image)
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            for attempt in range(60):
                try:
                    with opener.open("http://127.0.0.1:3001/api/ping", timeout=2) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    time.sleep(1)
            else:
                raise RuntimeError("Router did not become healthy")
            key = docker("exec", router, "node", "--input-type=module", "-e",
                         "import {getUnifiedApiKey} from './server/dist/db/index.js'; console.log(getUnifiedApiKey())")
            # Initialization can emit migration messages; accept only the key line.
            keys = [line for line in key.splitlines() if line.startswith("freellmapi-")]
            if len(keys) != 1:
                raise RuntimeError("Could not provision local router key")
            task = args.task_file
            if args.mode == "fixture":
                task = work / "task.txt"
                task.write_text("Add boundary tests for FbdPropertyEdits.", encoding="utf-8")
            if not task or not args.context:
                raise RuntimeError("Task and source context are required")
            # No provider key or GitHub credential is inherited by the agent.
            env = {k: v for k, v in os.environ.items() if k in ("PATH", "LANG", "SYSTEMROOT")}
            env.update(FREELLMAPI_BASE_URL="http://127.0.0.1:3001/v1", FREELLMAPI_API_KEY=keys[0])
            subprocess.run([sys.executable, str(root / "tools/cloud_coding_agent.py"),
                            "--task-file", str(task), "--model", model,
                            "--output", str(args.output), "--context", *args.context],
                           cwd=root, env=env, check=True, timeout=120)
            report_path = args.output / "report.json"
            report = json.loads(report_path.read_text())
            report.update(router_source=UPSTREAM_SHA, mode=args.mode,
                          provider="local fixture; no inference" if args.mode == "fixture" else "Groq free-tier",
                          live_inference_verified=args.mode == "live")
            report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
            print("Router contract completed (" + args.mode + "); patch awaits independent tests.")
        finally:
            for name in (router, fixture):
                subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=30)
            subprocess.run(["docker", "network", "rm", network], capture_output=True, timeout=30)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError):
        print("Cloud router pilot failed; credentials and raw router logs suppressed.", file=sys.stderr)
        raise SystemExit(1)
