#!/usr/bin/env python3
"""Daggerfall.apk: the launcher screen and the WebView game (no native code). javac -> d8 -> aapt2 -> zipalign -> apksigner,
with the same SDK tools and debug key as the Blizz4A build. VERSION: "<versionCode> <versionName>"."""
import os, shutil, subprocess, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk"))
TOOLS = SDK / "build-tools/35.0.0"
ANDROID_JAR = SDK / "platforms/android-35/android.jar"
KEY = HERE.parent / "android-combo/debug.keystore"
JAVA = Path(os.environ["JAVA_HOME"]) / "bin"

def run(*a):
    print("+", " ".join(map(str, a))[:200])
    subprocess.run(list(map(str, a)), check=True)

out = HERE / "out"
shutil.rmtree(out, ignore_errors=True)
(out / "classes").mkdir(parents=True)
(out / "dex").mkdir()
run(JAVA / "javac", "-source", "8", "-target", "8", "-nowarn", "-Xlint:-options", "-bootclasspath",
    os.pathsep.join(map(str, (TOOLS / "core-lambda-stubs.jar", ANDROID_JAR))), "-d", out / "classes",
    *sorted((HERE / "java").rglob("*.java")))
jar = out / "classes.jar"
with zipfile.ZipFile(jar, "w") as z:
    for p in sorted((out / "classes").rglob("*.class")):
        z.write(p, p.relative_to(out / "classes"))
run(TOOLS / "d8", "--lib", ANDROID_JAR, "--min-api", "33", "--output", out / "dex", jar)
run(TOOLS / "aapt2", "compile", "--dir", HERE / "res", "-o", out / "res.zip")
code, name = (HERE / "VERSION").read_text().split()[:2]
unsigned = out / "unsigned.apk"
run(TOOLS / "aapt2", "link", "-I", ANDROID_JAR, "--manifest", HERE / "AndroidManifest.xml", "--version-code", code,
    "--version-name", name, "-R", out / "res.zip", "--auto-add-overlay", "-o", unsigned)
with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
    apk.write(out / "dex/classes.dex", "classes.dex")
run(TOOLS / "zipalign", "-f", "-P", "16", "4", unsigned, out / "aligned.apk")
apk = HERE / "Daggerfall.apk"
run(TOOLS / "apksigner", "sign", "--ks", KEY, "--ks-pass", "pass:android", "--out", apk, out / "aligned.apk")
print(f"APK: {apk} ({apk.stat().st_size / 1048576:.1f} MB)")
