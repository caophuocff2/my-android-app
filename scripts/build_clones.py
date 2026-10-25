#!/usr/bin/env python3
"""Repackage independent installations without modifying executable code."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ANDROID = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", ANDROID[1:-1])


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def patch_manifest(original, package, label):
    root = ET.fromstring(original)
    old = root.attrib["package"]
    root.set("package", package)
    # Relative class names must continue resolving to the existing DEX classes.
    class_fields = {"name", "targetActivity", "backupAgent", "appComponentFactory", "manageSpaceActivity"}
    components = {"application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation"}
    for element in root.iter():
        for key, value in list(element.attrib.items()):
            field = key.removeprefix(ANDROID)
            if element.tag in components and field in class_fields:
                if value.startswith("."):
                    element.set(key, old + value)
                elif "." not in value:
                    element.set(key, old + "." + value)
            elif key.startswith(ANDROID) and old in value:
                element.set(key, value.replace(old, package))
        if element.tag in {"application", "activity-alias"}:
            element.set(ANDROID + "label", label)
        if element.tag in {"application", "activity"}:
            element.set(ANDROID + "resizeableActivity", "true")
    authorities = [e.get(ANDROID + "authorities", "") for e in root.iter("provider")]
    if any(old in a for a in authorities):
        raise RuntimeError("Provider authority still uses the source package")
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def executable_hashes(apk):
    with zipfile.ZipFile(apk) as z:
        return {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()
                if re.fullmatch(r"classes\d*\.dex", n) or n.startswith("lib/")}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--apktool", required=True, type=Path)
    parser.add_argument("--tools", required=True, type=Path)
    parser.add_argument("--keystore", required=True, type=Path)
    parser.add_argument("--count", type=int, default=11)
    args = parser.parse_args()
    if not 1 <= args.count <= 11:
        parser.error("count must be 1..11")
    source = args.source.resolve()
    tool = args.apktool.resolve()
    output = Path("dist")
    output.mkdir(exist_ok=True)
    decoded = Path("work/decoded")
    run("java", "-jar", tool, "d", source, "-s", "-f", "-o", decoded)
    manifest = decoded / "AndroidManifest.xml"
    original = manifest.read_bytes()
    original_hashes = executable_hashes(source)
    certificate = None
    records = []
    for number in range(1, args.count + 1):
        package = f"com.roblox.clone{number:02d}"
        label = f"Arceus QT Clone {number:02d}"
        manifest.write_bytes(patch_manifest(original, package, label))
        unsigned = Path("work/unsigned.apk")
        aligned = Path("work/aligned.apk")
        signed = output / f"ArceusQT-Clone-{number:02d}.apk"
        # Force recompilation so each package and its authorities are independent.
        run("java", "-jar", tool, "b", decoded, "-f", "-o", unsigned)
        run(args.tools / "zipalign", "-f", "-P", "16", "4", unsigned, aligned)
        run(args.tools / "apksigner", "sign", "--ks", args.keystore,
            "--ks-type", "PKCS12", "--ks-key-alias", "clone",
            "--ks-pass", "env:CLONE_KEYSTORE_PASSWORD", "--key-pass", "env:CLONE_KEYSTORE_PASSWORD",
            "--out", signed, aligned)
        verification = subprocess.check_output(
            [str(args.tools / "apksigner"), "verify", "--verbose", "--print-certs", str(signed)], text=True)
        match = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", verification)
        if match is None:
            raise RuntimeError("Could not verify signing certificate")
        current = match.group(1)
        if certificate is not None and current != certificate:
            raise RuntimeError("Signing certificates differ")
        certificate = current
        badging = subprocess.check_output([str(args.tools / "aapt"), "dump", "badging", str(signed)], text=True)
        if f"name='{package}'" not in badging or f"application-label:'{label}'" not in badging:
            raise RuntimeError(f"Package or label validation failed: {signed}")
        if executable_hashes(signed) != original_hashes:
            raise RuntimeError("Executable payload changed during packaging")
        run(args.tools / "zipalign", "-c", "-P", "16", "4", signed)
        records.append({"file": signed.name, "package": package, "label": label,
                        "sha256": hashlib.sha256(signed.read_bytes()).hexdigest()})
        print(f"VERIFIED {number}/{args.count}: {package}", flush=True)
    report = {"source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
              "certificate_sha256": certificate, "executable_payload_unchanged": True,
              "runtime_tested": False, "client_hwid_verified": False, "clones": records}
    (output / "build-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text(
        "".join(f"{r['sha256']}  {r['file']}\n" for r in records), encoding="utf-8")
    (output / "README.txt").write_text(
        "11 independent packages, all signed with one certificate.\n"
        "Install several APKs on the SAME Android device to use them side by side.\n"
        "Android ID depends on signing certificate, Android user and device.\n"
        "The client's own HWID scheme has NOT been verified.\n"
        "Runtime compatibility and simultaneous floating windows require device testing.\n"
        "Installing APKs does not itself enable floating-window support.\n", encoding="utf-8")


if __name__ == "__main__":
    main()
