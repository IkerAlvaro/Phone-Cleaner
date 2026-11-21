"""Genera los iconos (en PNG; después se convierten a WebP: ver el final de main) de Phone Cleaner a partir de tools/logo_source.png (el logo original).

Uso: python3 tools/make_icon.py  (requiere Pillow y numpy)

- Primer plano del icono adaptativo: la hoja y los destellos separados del fondo verde
  ("desmezcla" de color: el verde tiene R≈0 y los elementos blancos R alto).
- Monocromo para iconos temáticos, logo limpio para la app, icono de Play y PNG clásicos.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SRC = ROOT / "tools/logo_source.png"
# Cuadrado del logo dentro de la imagen original (medido).
BOX = (73, 61, 1180, 1187)
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# Degradado del fondo (coincide con drawable/ic_launcher_background.xml).
BG_LIGHT = np.array([0x2E, 0xE8, 0x80], float)
BG_DARK = np.array([0x00, 0xB0, 0x46], float)


def square() -> np.ndarray:
    img = Image.open(SRC).convert("RGBA").crop(BOX).resize((1024, 1024), Image.LANCZOS)
    return np.asarray(img).astype(float)


def background(size: int) -> np.ndarray:
    """Degradado radial: claro arriba a la izquierda, oscuro abajo a la derecha."""
    y, x = np.mgrid[0:size, 0:size] / (size - 1)
    t = np.clip(np.hypot(x - 0.15, y - 0.1) / 1.25, 0, 1)[..., None]
    return BG_LIGHT * (1 - t) + BG_DARK * t


def foreground_layer(sq: np.ndarray) -> Image.Image:
    rgb = sq[..., :3]
    # Fondo estimado: plano ajustado a los píxeles verdes (R bajo).
    h, w = rgb.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    green = (rgb[..., 0] < 60) & (sq[..., 3] > 250)
    A = np.c_[xx[green], yy[green], np.ones(green.sum())]
    bg = np.stack([np.linalg.lstsq(A, rgb[..., c][green], rcond=None)[0] @ np.stack([xx, yy, np.ones_like(xx)]).reshape(3, -1)
                   for c in range(3)], -1).reshape(h, w, 3)
    bg = np.clip(bg, 0, 255)
    # Alfa a partir del canal rojo: 0 sobre el verde, 1 en blanco.
    alpha = np.clip((rgb[..., 0] - bg[..., 0] - 20) / 130.0, 0, 1)
    # Los bordes del cuadrado tienen un brillo que no forma parte del dibujo.
    m = 140
    alpha[:m, :] = 0; alpha[-m:, :] = 0; alpha[:, :m] = 0; alpha[:, -m:] = 0
    fg = np.where(alpha[..., None] > 0.02,
                  (rgb - (1 - alpha[..., None]) * bg) / np.maximum(alpha[..., None], 0.02), 255)
    fg = np.clip(fg, 0, 255)
    rgba = np.dstack([fg, alpha * 255]).astype(np.uint8)
    art = Image.fromarray(rgba, "RGBA")
    # Recentrar: el dibujo debe caber en la zona segura (66 de 108 dp).
    bbox = art.getbbox()
    art = art.crop(bbox)
    canvas = 1024
    target = int(canvas * 50 / 108)
    scale = target / max(art.size)
    art = art.resize((max(1, int(art.width * scale)), max(1, int(art.height * scale))), Image.LANCZOS)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.paste(art, ((canvas - art.width) // 2, (canvas - art.height) // 2), art)
    # Sombra suave debajo, como en el original.
    shadow = Image.new("RGBA", layer.size, (0, 60, 20, 0))
    sa = layer.split()[3].filter(ImageFilter.GaussianBlur(18)).point(lambda v: int(v * 0.35))
    shadow.putalpha(sa)
    out = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    out.alpha_composite(shadow, (0, 14))
    out.alpha_composite(layer)
    return out


def composite(fg: Image.Image, size: int) -> Image.Image:
    bg = Image.fromarray(background(size).astype(np.uint8), "RGB").convert("RGBA")
    bg.alpha_composite(fg.resize((size, size), Image.LANCZOS))
    return bg


def rounded(img: Image.Image, radius_frac: float) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    r = int(img.width * radius_frac)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], r, fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


def main() -> None:
    sq = square()
    fg = foreground_layer(sq)
    for name, k in DENSITIES.items():
        d = RES / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        layer_px = int(108 * k)
        fg.resize((layer_px, layer_px), Image.LANCZOS).save(d / "ic_launcher_foreground.png", optimize=True)
        mono = Image.new("RGBA", (layer_px, layer_px), (0, 0, 0, 0))
        mono.putalpha(fg.resize((layer_px, layer_px), Image.LANCZOS).split()[3])
        mono.save(d / "ic_launcher_monochrome.png", optimize=True)
        # Iconos clásicos (Android < 8): 48 dp con el logo original.
        legacy_px = int(48 * k)
        inner = Image.fromarray(sq.astype(np.uint8), "RGBA").resize((legacy_px, legacy_px), Image.LANCZOS)
        rounded(inner, 0.22).save(d / "ic_launcher.png", optimize=True)
        circle = Image.new("L", inner.size, 0)
        ImageDraw.Draw(circle).ellipse([0, 0, legacy_px - 1, legacy_px - 1], fill=255)
        rnd = inner.copy(); rnd.putalpha(circle); rnd.save(d / "ic_launcher_round.png", optimize=True)
    # Logo de la app: el original limpio (sin restos verdes fuera del cuadrado).
    nodpi = RES / "drawable-nodpi"; nodpi.mkdir(exist_ok=True)
    logo = Image.fromarray(sq.astype(np.uint8), "RGBA").resize((512, 512), Image.LANCZOS)
    rounded(logo, 0.24).save(nodpi / "app_logo.png", optimize=True)
    # Icono para Google Play: 512×512, a sangre y sin transparencia.
    # (Play aplica su propia máscara redondeada.)
    Image.fromarray(sq.astype(np.uint8), "RGBA").convert("RGB").resize((512, 512), Image.LANCZOS) \
        .save(ROOT / "docs/play-store-icon-512.png", optimize=True)
    to_webp()


def to_webp() -> None:
    """WebP con calidad 90: mismo aspecto y bastante menos peso en el APK."""
    for p in list(RES.glob("mipmap-*/*.png")) + [RES / "drawable-nodpi/app_logo.png"]:
        Image.open(p).save(p.with_suffix(".webp"), "WEBP", quality=90, method=6)
        p.unlink()


if __name__ == "__main__":
    main()
