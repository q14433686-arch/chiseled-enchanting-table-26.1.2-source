#!/usr/bin/env python3
"""Boot a disposable Loom dedicated MC 26.1.2 server for Enchantment Fusion;
verify real block/item/datapack/mixin load, Enchantment Fusion Table placement & block loot, and /reload.
"""
import json
import os
from pathlib import Path
import queue
import shutil
import signal
import subprocess
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "run"
RUN.mkdir(exist_ok=True)
if (RUN / "world").exists() or (RUN / "audit-world").exists():
    raise SystemExit("Refusing to use an existing world; smoke test requires a fresh checkout")

(RUN / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(RUN / "server.properties").write_text(
    "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
    "level-name=audit-world\nlevel-type=minecraft:flat\n"
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2}],"biome":"minecraft:plains","lakes":false,"features":false}\n'
    "generate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=60000\n",
    encoding="utf-8",
)

fixture_root = ROOT / "src/main/resources/data/ef_audit_smoke"
if fixture_root.exists():
    raise SystemExit("Refusing to overwrite an existing audit fixture namespace")

fixture_functions = fixture_root / "function"
fixture_functions.mkdir(parents=True)
(fixture_functions / "verify_station.mcfunction").write_text(
    "setblock 0 65 0 enchantment_fusion:enchantment_fusion_table\n"
    'data merge block 0 65 0 {Items:[{Slot:3b,id:"minecraft:diamond_sword",count:1}]}\n'
    "loot spawn 0 67 0 mine 0 65 0 minecraft:diamond_pickaxe\n"
    "schedule function ef_audit_smoke:station_ready 10t\n",
    encoding="utf-8",
)
(fixture_functions / "station_ready.mcfunction").write_text(
    "say EF_AUDIT_STATION_READY\n",
    encoding="utf-8",
)

log_path = ROOT / "build-reports/server-smoke.log"
log_path.parent.mkdir(exist_ok=True)

lines_q: queue.Queue[str] = queue.Queue()


def pump(stream, log_file):
    for raw in iter(stream.readline, ""):
        log_file.write(raw)
        log_file.flush()
        lines_q.put(raw)


try:
    with log_path.open("w", encoding="utf-8") as log_file:
        proc = subprocess.Popen(
            [
                "./gradlew",
                "runServer",
                "-Penable_jei=false",
                "-Penable_rei=false",
                "--console=plain",
                "--no-daemon",
            ],
            cwd=ROOT,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            start_new_session=True,
        )
        reader = threading.Thread(target=pump, args=(proc.stdout, log_file), daemon=True)
        reader.start()

        def wait_for(pattern: str, timeout_s: float) -> str:
            deadline = time.monotonic() + timeout_s
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    raise RuntimeError(f"Server exited early ({proc.returncode}) while waiting for {pattern!r}")
                try:
                    line = lines_q.get(timeout=1.0)
                except queue.Empty:
                    continue
                if "ERROR" in line and ("EnchantmentFusion" in line or "Mixin apply" in line):
                    raise RuntimeError(f"Fatal error in server log: {line.strip()}")
                if pattern in line:
                    return line
            raise TimeoutError(f"Timed out waiting for {pattern!r}")

        def send(cmd: str) -> None:
            assert proc.stdin is not None
            proc.stdin.write(cmd + "\n")
            proc.stdin.flush()

        try:
            wait_for("Done (", 420.0)
            send("function ef_audit_smoke:verify_station")
            wait_for("EF_AUDIT_STATION_READY", 60.0)
            send("data get block 0 65 0 Items")
            wait_for("minecraft:diamond_sword", 30.0)
            send("reload")
            wait_for("Loaded Enchantment Fusion matrix", 60.0)
            send("stop")
            proc.wait(timeout=60.0)
        finally:
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
                try:
                    proc.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL)
finally:
    shutil.rmtree(fixture_root, ignore_errors=True)

full_log = log_path.read_text(encoding="utf-8", errors="replace")
assert "Enchantment Fusion initialized" in full_log, "ModInitializer did not run on 26.1.2 server"
assert "Loaded Enchantment Fusion matrix" in full_log, "FusionDataLoader did not load matrix profiles"
print("SERVER SMOKE PASS: MC 26.1.2 server boot + Enchantment Fusion Table block entity + /reload matrix snapshot")
