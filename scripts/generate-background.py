from pathlib import Path
from PIL import Image

# Re-export the selected B background without replacing it with procedural artwork.
root = Path(__file__).resolve().parents[1]
source = root / 'docs/design-assets/space-b-nebula.png'
output = root / 'app/src/main/res/drawable-nodpi/cosmic_nebula.webp'
output.parent.mkdir(parents=True, exist_ok=True)
with Image.open(source) as image:
    image.convert('RGB').save(output, format='WEBP', quality=90, method=6)
print(output)