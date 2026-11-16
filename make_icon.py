"""Launcher icon: Daggerfall Online's own icon (the site's icons/icon-512-maskable.png, used at its developer's request),
scaled to the mipmap densities; the maskable art keeps the emblem inside the shape Android cuts it to."""
from pathlib import Path
from PIL import Image
HERE = Path(__file__).resolve().parent
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
src = Image.open(HERE / "icon-source.png").convert("RGB")
for dens, n in SIZES.items():
    out = HERE / "res" / f"mipmap-{dens}"
    out.mkdir(parents=True, exist_ok=True)
    src.resize((n, n), Image.LANCZOS).save(out / "ic_launcher.png")
