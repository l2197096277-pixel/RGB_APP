"""把项目中的旧自定义 UUID 迁移到中科蓝讯的 16 位 UUID 基址。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAPPING = {
    "A0E1B2C3-0001-4D5E-8F90-A1B2C3D4E5F6": "0000BBB0-0000-1000-8000-00805F9B34FB",
    "A0E1B2C3-0002-4D5E-8F90-A1B2C3D4E5F6": "0000BBB1-0000-1000-8000-00805F9B34FB",
    "A0E1B2C3-0003-4D5E-8F90-A1B2C3D4E5F6": "0000BBB2-0000-1000-8000-00805F9B34FB",
    "A0E1B2C3-0001-...": "0000BBB0-0000-1000-8000-00805F9B34FB",
}
TEXT_SUFFIXES = {".java", ".ino", ".md", ".txt", ".html", ".xml", ".gradle", ".properties"}

for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
        continue
    if any(part in {".git", "build", "dist", ".gradle"} for part in path.parts):
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    original = text
    for old, new in MAPPING.items():
        text = text.replace(old, new)
    if text != original:
        path.write_text(text, encoding="utf-8")
        print("updated", path.relative_to(ROOT))

left = []
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
        continue
    if any(part in {".git", "build", "dist", ".gradle"} for part in path.parts):
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    if "A0E1B2C3" in text:
        left.append(str(path.relative_to(ROOT)))
print("remaining", left)
