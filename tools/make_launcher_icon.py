# -*- coding: utf-8 -*-
"""런처 아이콘 레이어 생성기 — 시안 PNG 한 장에서 적응형 아이콘 두 레이어를 뽑는다 (D-77).

    python tools/make_launcher_icon.py        # app/app/src/main/res/mipmap-*/ 에 덮어쓴다

- 바깥 배경을 지워 전경(핀)만 남기고, 시안의 청록을 브랜드 딥그린(#2E6B4F)으로 옮긴다.
- 내용의 최대 반지름을 안전영역(중앙 66dp) 안에 맞춘다. 이걸 안 하면 원형 마스크에서 잘린다.
- 단색(monochrome) 레이어는 밝은 곳을 뚫어 실루엣으로 만든다. Android 13+ 테마 아이콘이 쓴다.

필요: pillow, numpy
"""
import sys, math
from PIL import Image, ImageDraw, ImageFilter

SRC = '이태우_디자인시안/miripet-icon-compass.png'
CREAM = (253, 242, 225)
TEAL  = (21, 144, 143)
GREEN = (0x2E, 0x6B, 0x4F)

def extract(src=SRC):
    """모서리에서 flood fill 로 바깥 배경만 지운다. 발바닥(핀 안쪽 크림)은 남는다."""
    im = Image.open(src).convert('RGBA')
    w, h = im.size
    # 알파를 만들 마스크: 바깥 배경 = 0
    rgb = im.convert('RGB')
    mask = Image.new('L', (w, h), 255)
    ImageDraw.floodfill(rgb, (2, 2), (255, 0, 255), thresh=60)
    ImageDraw.floodfill(rgb, (w-3, 2), (255, 0, 255), thresh=60)
    ImageDraw.floodfill(rgb, (2, h-3), (255, 0, 255), thresh=60)
    ImageDraw.floodfill(rgb, (w-3, h-3), (255, 0, 255), thresh=60)
    px = rgb.load(); mp = mask.load()
    for y in range(h):
        for x in range(w):
            if px[x, y] == (255, 0, 255):
                mp[x, y] = 0
    # 계단 완화
    mask = mask.filter(ImageFilter.GaussianBlur(1.2))
    out = im.copy(); out.putalpha(mask)
    return out.crop(out.getbbox())

def recolor(im, frm=TEAL, to=GREEN, base=CREAM):
    """frm↔base 축 위에 있는 픽셀만 to 로 옮긴다. 안티에일리어싱 가장자리도 같이 따라온다.
       오렌지 뱃지는 그 축에서 멀어 그대로 남는다."""
    import numpy as np
    a = np.asarray(im).astype(np.float32)
    rgb, alpha = a[..., :3], a[..., 3:]
    b = np.array(base, np.float32); f = np.array(frm, np.float32); t = np.array(to, np.float32)
    d = f - b
    k = ((rgb - b) @ d) / (d @ d)                      # 축 위 위치 (0=cream, 1=teal)
    resid = rgb - (b + k[..., None] * d)               # 축에서 벗어난 정도
    on_axis = (np.linalg.norm(resid, axis=-1) < 60)    # 오렌지 제외
    kk = np.clip(k, 0, 1)[..., None]
    new = b + kk * (t - b) + resid
    out = np.where(on_axis[..., None], new, rgb)
    return Image.fromarray(np.concatenate([np.clip(out, 0, 255), alpha], -1).astype('uint8'), 'RGBA')

def layer(content, size=432, safe_ratio=66/108):
    """내용의 최대 반지름이 안전영역 원 안에 들어오도록 축소해 정중앙에 놓는다."""
    import numpy as np
    c = content
    # 최대 반지름 계산 (불투명 픽셀 기준)
    a = np.asarray(c)[..., 3] > 40
    ys, xs = np.nonzero(a)
    cx, cy = (xs.min()+xs.max())/2, (ys.min()+ys.max())/2
    maxr = float(np.sqrt(((xs-cx)**2 + (ys-cy)**2).max()))
    target_r = size * safe_ratio / 2
    s = target_r / maxr
    nw, nh = max(1, round(c.width*s)), max(1, round(c.height*s))
    c2 = c.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(c2, (round(size/2 - (cx*s)), round(size/2 - (cy*s))))
    return canvas

def mono(content):
    """단색 레이어: 실루엣을 검정 + 알파로. 발바닥·화살표는 구멍으로 남는다.

    ⚠️ **문턱은 크림(240)과 주황 뱃지(168) 사이가 아니라 크림 바로 아래여야 한다** (D-90).
    150~210 으로 잡으면 뱃지가 알파 0.7 로 남아, 테마 아이콘에서 **흐릿한 얼룩**으로 보인다
    (실기기 실측). 단색 레이어는 색이 없고 알파만 있으므로 반투명은 그냥 때처럼 읽힌다.
    뱃지를 핀 몸통과 같은 불투명으로 합치면 **화살표 구멍만** 남아 또렷해진다.
    """
    import numpy as np
    a = np.asarray(content).astype(np.float32)
    rgb, alpha = a[..., :3], a[..., 3]
    # 크림(밝은) 픽셀만 뚫는다. 폭 30 은 가장자리 안티에일리어싱을 살리기 위한 것이다.
    lum = rgb.mean(-1)
    keep = np.clip((225 - lum) / 30, 0, 1) * (alpha / 255)
    out = np.zeros_like(a); out[..., 3] = keep * 255
    return Image.fromarray(out.astype('uint8'), 'RGBA')

DENSITIES = [('mdpi',108), ('hdpi',162), ('xhdpi',216), ('xxhdpi',324), ('xxxhdpi',432)]

def write(content, res='app/app/src/main/res'):
    base = layer(content, 432)
    m = mono(base)
    for name, px in DENSITIES:
        d = f'{res}/mipmap-{name}'
        base.resize((px, px), Image.LANCZOS).save(f'{d}/ic_launcher_foreground.png')
        m.resize((px, px), Image.LANCZOS).save(f'{d}/ic_launcher_monochrome.png')
    return base, m


if __name__ == '__main__':
    import os
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
    content = recolor(extract())
    write(content)
    for name, px in DENSITIES:
        print(f'mipmap-{name:8s} {px:3d}px  전경 + 단색')
