from PIL import Image, ImageDraw

sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

base = '/Users/renhongqiang/Downloads/code/voice-bridge/android/app/src/main/res'

for density, size in sizes.items():
    img = Image.new('RGBA', (size, size), (98, 0, 238, 255))
    draw = ImageDraw.Draw(img)
    margin = size // 6
    draw.ellipse([margin, margin, size - margin, size - margin], outline=(255, 255, 255, 255), width=max(2, size // 24))
    cx, cy = size // 2, size // 2
    mw, mh = size // 8, size // 5
    draw.rounded_rectangle([cx - mw, cy - mh - size//10, cx + mw, cy + mh - size//10], radius=mw, fill=(255, 255, 255, 255))
    draw.line([(cx, cy + mh - size//10), (cx, cy + mh + size//10)], fill=(255, 255, 255, 255), width=max(2, size // 24))
    draw.arc([cx - mw - size//12, cy + mh - size//8, cx + mw + size//12, cy + mh + size//6], 0, 180, fill=(255, 255, 255, 255), width=max(2, size // 24))

    img.save(f'{base}/mipmap-{density}/ic_launcher.png')
    img.save(f'{base}/mipmap-{density}/ic_launcher_round.png')
    print(f'Created {density} icons: {size}x{size}')
