"""用 UTF-8 文件名重新打包源码包和文档包。"""
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "dist")


def add_file(zf, full, arc):
    info = zipfile.ZipInfo.from_file(full, arc)
    info.compress_type = zipfile.ZIP_DEFLATED
    with open(full, "rb") as fh:
        zf.writestr(info, fh.read(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build_source():
    out = os.path.join(DIST, "AuroraLight-v1.1.0-source.zip")
    if os.path.exists(out):
        os.remove(out)
    skip_dirs = {"build", "dist", ".buildtools", "__pycache__", ".gradle", ".idea"}
    with zipfile.ZipFile(out, "w") as zf:
        for root, dirs, files in os.walk(ROOT):
            dirs[:] = [d for d in dirs if d not in skip_dirs]
            for name in files:
                if name.endswith(".idsig") or name == "local.properties":
                    continue
                full = os.path.join(root, name)
                arc = os.path.relpath(full, ROOT)
                add_file(zf, full, arc)
        add_file(zf, os.path.join(DIST, "BUILD-INFO.txt"), "BUILD-INFO.txt")
        add_file(zf, os.path.join(DIST, "安装说明.txt"), "安装说明.txt")
    print(out, os.path.getsize(out))


def build_docs():
    out = os.path.join(DIST, "AuroraLight-v1.1.0-docs.zip")
    if os.path.exists(out):
        os.remove(out)
    with zipfile.ZipFile(out, "w") as zf:
        for folder in ("docs", "firmware"):
            base = os.path.join(ROOT, folder)
            for root, dirs, files in os.walk(base):
                dirs[:] = [d for d in dirs if d != "__pycache__"]
                for name in files:
                    full = os.path.join(root, name)
                    arc = os.path.relpath(full, ROOT)
                    add_file(zf, full, arc)
        add_file(zf, os.path.join(ROOT, "README.md"), "README.md")
        add_file(zf, os.path.join(DIST, "安装说明.txt"), "安装说明.txt")
    print(out, os.path.getsize(out))


if __name__ == "__main__":
    build_source()
    build_docs()

