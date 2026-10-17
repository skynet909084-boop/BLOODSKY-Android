#!/usr/bin/env python3
"""Start an ephemeral pinned FreeLLMAPI router and run the bounded agent.

Fixture mode uses an internal Docker network: no provider credentials or
outbound internet. Live mode uses only one explicitly selected Groq free model.
Anonymous mode uses OVHcloud without an account or provider credentials.
Never upload router logs, its database, generated keys or startup configuration.
"""
import argparse
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
ANONYMOUS_MODEL = "gpt-oss-120b"
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



def docker(*args, **kwargs):
    # Capture output because Docker/node diagnostics may include configuration.
    result = subprocess.run(["docker", *args], capture_output=True, text=True,
                            timeout=kwargs.pop("timeout", 60), **kwargs)
    if result.returncode:
        raise RuntimeError("Docker operation failed: " + args[0])
    return result.stdout.strip()


def wait_for_router(opener, attempts=60):
    """Published ports can reset connections while Node is still starting."""
    for _ in range(attempts):
        try:
            with opener.open("http://127.0.0.1:3001/api/ping", timeout=2) as response:
                if response.status == 200:
                    return True
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            if isinstance(error, urllib.error.HTTPError):
                error.close()
        time.sleep(1)
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("fixture", "live", "anonymous"), default="fixture")
    parser.add_argument("--image", default="bloodsky-freellmapi:pilot")
    parser.add_argument("--task-file", type=Path)
    parser.add_argument("--context", nargs="+")
    parser.add_argument("--output", type=Path, default=Path("cloud-output"))
    args = parser.parse_args()
    if args.mode == "live" and (os.environ.get("BLOODSKY_FREE_TIER_CONFIRMED") != "true"
                                or not os.environ.get("GROQ_FREE_API_KEY")):
        raise RuntimeError("Live mode requires confirmed free-only Groq account and GROQ_FREE_API_KEY")
    suffix = secrets.token_hex(6)
    router, fixture, network = "bloodsky-router-" + suffix, "bloodsky-fixture-" + suffix, "bloodsky-" + suffix
    relay, front = "bloodsky-relay-" + suffix, "bloodsky-front-" + suffix
    root = Path(__file__).resolve().parents[1]
    model = {"fixture": FIXTURE_MODEL, "live": LIVE_MODEL, "anonymous": ANONYMOUS_MODEL}[args.mode]
    with tempfile.TemporaryDirectory(prefix="bloodsky-router-") as temp:
        work = Path(temp)
        config = {"admin": {"email": "pilot@example.invalid", "password": secrets.token_hex(24)},
                  "routing": {"strategy": "priority"}}
        if args.mode == "fixture":
            config["customProviders"] = [{"baseUrl": "http://" + fixture + ":3002/v1",
                                           "apiKey": "fixture-only-not-a-provider-key",
                                           "models": [{"model": model, "supportsTools": False}]}]
            config["fallback"] = [{"platform": "custom", "modelId": model, "priority": 1}]
        elif args.mode == "live":
            config["keys"] = [{"platform": "groq", "key": os.environ["GROQ_FREE_API_KEY"]}]
            config["fallback"] = [{"platform": "groq", "modelId": model, "priority": 1}]
        else:
            # Official anonymous OVH endpoint: no account, key or billing data.
            config["keys"] = [{"platform": "ovh"}]
            config["fallback"] = [{"platform": "ovh", "modelId": model, "priority": 1}]
        env_file = work / "router.env"
        env_file.write_text("ENCRYPTION_KEY=" + secrets.token_hex(32)
                            + "\nNODE_ENV=production\nHOST=0.0.0.0\nCATALOG_SYNC_DISABLED=1\n"
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
            ports = [] if args.mode == "fixture" else ["-p", "127.0.0.1:3001:3001"]
            docker("run", "-d", "--name", router, "--network", network,
                   *ports, "--env-file", str(env_file), args.image)
            if args.mode == "fixture":
                # Docker does not publish ports for an internal-only network.
                # A fixed-target relay exposes loopback without giving either
                # the router or fixture an internet route. It cannot proxy to
                # an arbitrary destination supplied by a request.
                docker("network", "create", front)
                proxy = ("const h=require('http');h.createServer((q,s)=>{"
                         "const r=h.request({hostname:" + json.dumps(router) + ",port:3001,"
                         "path:q.url,method:q.method,headers:q.headers},p=>{s.writeHead(p.statusCode,p.headers);p.pipe(s);});"
                         "r.on('error',()=>{s.writeHead(502);s.end();});q.pipe(r);}).listen(3003,'0.0.0.0');")
                docker("run", "-d", "--name", relay, "--network", front,
                       "-p", "127.0.0.1:3001:3003", "--entrypoint", "node", args.image, "-e", proxy)
                docker("network", "connect", network, relay)
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            if not wait_for_router(opener):
                state = json.loads(docker("inspect", "--format", "{{json .State}}", router))
                logs = subprocess.run(["docker", "logs", router], capture_output=True, text=True, timeout=10)
                # Emit only known constant error categories, never raw log text.
                markers = {"custom-api-key-required": "key is required for custom",
                           "invalid-startup-config": "invalid declarative config",
                           "sqlite-module-missing": "better-sqlite3 is not installed",
                           "encryption-key-required": "ENCRYPTION_KEY is required",
                           "file-permission-denied": "EACCES",
                           "module-not-found": "ERR_MODULE_NOT_FOUND"}
                category = [name for name, marker in markers.items() if marker in logs.stdout + logs.stderr]
                raise RuntimeError("Router did not become healthy (status=" + state.get("Status", "unknown")
                                   + ", exit=" + str(state.get("ExitCode")) + ", categories=" + str(category) + ")")
            platform = {"fixture": "custom", "live": "groq", "anonymous": "ovh"}[args.mode]
            provision = ("import {connectDb,getUnifiedApiKey} from './server/dist/db/index.js';"
                         "const db=connectDb();const platform=" + json.dumps(platform) + ",model=" + json.dumps(model) + ";"
                         "if(!db.prepare('SELECT id FROM models WHERE platform=? AND model_id=?').get(platform,model))"
                         "throw new Error('Selected pilot model is absent from the pinned catalog');"
                         "db.prepare('UPDATE models SET enabled=CASE WHEN platform=? AND model_id=? THEN 1 ELSE 0 END').run(platform,model);"
                         "db.prepare('UPDATE fallback_config SET enabled=0 WHERE model_db_id NOT IN "
                         "(SELECT id FROM models WHERE platform=? AND model_id=?)').run(platform,model);"
                         "db.prepare('UPDATE api_keys SET enabled=0 WHERE platform<>?').run(platform);"
                         "console.log(getUnifiedApiKey());db.close();")
            key = docker("exec", "--user", "node", router, "node", "--input-type=module", "-e", provision)
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
            budget = ["--max-tokens", "1024", "--reasoning-effort", "low"] if args.mode == "anonymous" else []
            subprocess.run([sys.executable, str(root / "tools/cloud_coding_agent.py"),
                            "--task-file", str(task), "--model", model,
                            "--output", str(args.output), *budget, "--context", *args.context],
                           cwd=root, env=env, check=True, timeout=120)
            report_path = args.output / "report.json"
            report = json.loads(report_path.read_text())
            report.update(router_source=UPSTREAM_SHA, mode=args.mode,
                          provider={"fixture": "local fixture; no inference", "live": "Groq free-tier",
                                    "anonymous": "OVHcloud anonymous; no account credentials"}[args.mode],
                          live_inference_verified=args.mode != "fixture")
            report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
            print("Router contract completed (" + args.mode + "); patch awaits independent tests.")
        finally:
            for name in (relay, router, fixture):
                subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=30)
            for name in (front, network):
                subprocess.run(["docker", "network", "rm", name], capture_output=True, timeout=30)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print("Cloud router pilot failed: " + str(error) + "; raw router logs suppressed.", file=sys.stderr)
        raise SystemExit(1)
    except subprocess.SubprocessError:
        print("Cloud router pilot failed; credentials and raw router logs suppressed.", file=sys.stderr)
        raise SystemExit(1)
