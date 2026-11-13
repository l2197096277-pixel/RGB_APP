"""把 d8 生成的 classes.dex 放进 aapt2 产出的 APK 中。"""
import os
import sys
import zipfile

if len(sys.argv) != 4:
    raise SystemExit("usage: add_dex.py <in.apk> <out.apk> <classes.dex>")

src, dst, dex = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(dst, "w") as zout:
    for item in zin.infolist():
        if item.filename == "classes.dex":
            continue
        data = zin.read(item.filename)
        new_item = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        new_item.compress_type = item.compress_type
        new_item.external_attr = item.external_attr
        new_item.internal_attr = item.internal_attr
        new_item.create_system = item.create_system
        zout.writestr(new_item, data)
    zout.write(dex, "classes.dex", compress_type=zipfile.ZIP_DEFLATED)

print(os.path.getsize(dst))
