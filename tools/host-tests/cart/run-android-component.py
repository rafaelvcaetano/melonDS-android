#!/usr/bin/env python3
"""Run the authored NAND cart component on one already-started diagnostic AVD."""

import argparse
import csv
import hashlib
import pathlib
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True, choices=("emulator-5554", "emulator-5556"))
    parser.add_argument("--pagesize", required=True, choices=("4096", "16384"))
    args = parser.parse_args()
    repo = pathlib.Path(__file__).resolve().parents[3]
    adb = pathlib.Path.home() / "Library/Android/sdk/platform-tools/adb"
    command = [str(adb), "-s", args.serial]
    output = repo / "local/reports/ds-2026-11-03/nand-cart-read"
    output.mkdir(parents=True, exist_ok=True)
    binary = repo / "build/host-tests/cart/NandReadSpanTest-android35-arm64-appcore"

    def call(*items):
        result = subprocess.run(command + list(items), stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=30, check=True)
        return result.stdout.decode(errors="replace").strip()

    for _ in range(20):
        try:
            if call("shell", "getprop sys.boot_completed") == "1":
                break
        except subprocess.CalledProcessError:
            pass
        time.sleep(1)
    else:
        raise RuntimeError("diagnostic AVD boot timeout")

    identity = {
        "sdk": call("shell", "getprop ro.build.version.sdk"),
        "abi": call("shell", "getprop ro.product.cpu.abi"),
        "pagesize": call("shell", "getconf PAGESIZE"),
    }
    if identity != {"sdk": "35", "abi": "arm64-v8a", "pagesize": args.pagesize}:
        raise RuntimeError(f"unexpected AVD identity: {identity}")
    print(identity)

    remote = call("shell", "mktemp -d /data/local/tmp/melonds-nandcart.XXXXXXXX")
    if not re.fullmatch(r"/data/local/tmp/melonds-nandcart\.[A-Za-z0-9]{8}", remote):
        raise RuntimeError(f"unexpected test directory: {remote}")
    remote_binary = remote + "/NandReadSpanTest"
    suffix = "4k" if args.pagesize == "4096" else "16k"
    rows = [["mode", "exit_code"]]
    logs = []
    clean_ready = False
    try:
        call("push", str(binary), remote_binary)
        call("shell", "chmod 700 " + remote_binary)
        for mode in ("valid", "invalid-window", "outside-start", "page-wrap",
                     "near-end-exact", "near-end"):
            result = subprocess.run(command + ["shell", "env",
                "UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1",
                remote_binary, mode], stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT, timeout=20)
            data = result.stdout.decode(errors="replace")
            rows.append([mode, result.returncode])
            logs.append(f"MODE {mode} EXIT {result.returncode}\n{data}")
            print(mode, result.returncode, data[-110:].replace("\n", " | "))

        (output / f"post-android-{suffix}.log").write_text("\n".join(logs))
        with (output / f"post-android-{suffix}-status.csv").open("w", newline="") as f:
            csv.writer(f).writerows(rows)
        if any(int(row[1]) != 0 for row in rows[1:]):
            raise RuntimeError("component case failed")

        local_hash = hashlib.sha256(binary.read_bytes()).hexdigest()
        remote_hash = call("shell", "sha256sum " + remote_binary).split()[0]
        if local_hash != remote_hash:
            raise RuntimeError("remote test binary differs from local candidate")
        with (output / f"cleanup-android-{suffix}.csv").open("w", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(("path", "bytes", "sha256", "reason", "canonical_copy"))
            writer.writerow((remote_binary, binary.stat().st_size, remote_hash,
                             "completed isolated AVD final wrapping run", str(binary)))
        clean_ready = True
    finally:
        if clean_ready:
            call("shell", "rm -f " + remote_binary)
            call("shell", "rmdir " + remote)
            print("REMOTE_CLEANED", remote)
        else:
            print("REMOTE_RETAINED_FOR_INSPECTION", remote)


if __name__ == "__main__":
    main()
