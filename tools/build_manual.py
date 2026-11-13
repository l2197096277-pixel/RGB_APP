"""把项目 Markdown 文档排版成一份可打印的 HTML（再由 Edge 导出 PDF）。"""
import base64
import glob
import os
import re
import markdown

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
OUT = os.path.join(DOCS, "_manual.html")

with open(os.path.join(ROOT, "assets", "icon-master.png"), "rb") as f:
    icon_b64 = base64.b64encode(f.read()).decode("ascii")

parts = []
for name in ("使用手册.md", "BLE协议规范.md", "固件烧录指南.md", "自动更新说明.md", "开发与构建说明.md", "CHANGELOG.md"):
    path = os.path.join(ROOT, name) if name == "CHANGELOG.md" else os.path.join(DOCS, name)
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    parts.append(markdown.markdown(
        text,
        extensions=["tables", "fenced_code", "toc", "sane_lists"],
        output_format="html5",
    ))

body = "\n".join(parts)

# 去掉 Markdown 生成的多余目录（保留正文标题），统一由封面后的章节开始
body = re.sub(r'<div class="toc">.*?</div>\s*', "", body, flags=re.S)

shots = []
preview_path = os.path.join(DOCS, "design-preview.png")
if os.path.exists(preview_path):
    with open(preview_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("ascii")
    shots.append(
        f'<figure class="tall"><img src="data:image/png;base64,{b64}" '
        f'alt="界面设计预览"><figcaption>主界面 · 设计预览</figcaption></figure>'
    )
scan_preview = os.path.join(DOCS, "scan-preview.png")
if os.path.exists(scan_preview):
    with open(scan_preview, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("ascii")
    shots.append(
        f'<figure class="tall"><img src="data:image/png;base64,{b64}" '
        f'alt="连接面板设计预览"><figcaption>连接面板 · 设计预览</figcaption></figure>'
    )
for path in sorted(glob.glob(os.path.join(DOCS, "screenshots", "*.png"))):
    with open(path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("ascii")
    caption = os.path.splitext(os.path.basename(path))[0]
    shots.append(
        f'<figure><img src="data:image/png;base64,{b64}" alt="{caption}">'
        f'<figcaption>{caption}</figcaption></figure>'
    )
shots_html = ""
if shots:
    shots_html = (
        '<h1 id="screenshots">界面预览</h1>'
        '<p class="lead">主界面采用深色极光、玻璃卡片和高对比渐变控件；下图为与实现共用同一视觉规范的设计预览。'
        '实际 Android 渲染会因机型、字体和状态栏高度略有差异。</p>'
        '<div class="shots">' + "".join(shots) + "</div>"
    )

html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>幻彩灯控 · 使用与协议文档</title>
<style>
  @page {{
    size: A4;
    margin: 18mm 16mm 18mm 16mm;
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0;
    color: #1b2233;
    background: #ffffff;
    font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", sans-serif;
    font-size: 10.2pt;
    line-height: 1.72;
  }}
  .cover {{
    height: 250mm;
    page-break-after: always;
    background:
      radial-gradient(circle at 20% 18%, rgba(91, 240, 208, 0.34) 0, rgba(91, 240, 208, 0) 38%),
      radial-gradient(circle at 86% 24%, rgba(139, 123, 255, 0.36) 0, rgba(139, 123, 255, 0) 42%),
      radial-gradient(circle at 48% 98%, rgba(255, 122, 198, 0.28) 0, rgba(255, 122, 198, 0) 44%),
      #05070f;
    color: #f6f8ff;
    border-radius: 5mm;
    padding: 24mm 20mm;
    position: relative;
    overflow: hidden;
  }}
  .cover .eyebrow {{
    font-size: 8.5pt;
    letter-spacing: 4px;
    color: #5bf0d0;
    font-weight: 700;
  }}
  .cover h1 {{
    font-size: 36pt;
    line-height: 1.2;
    margin: 12mm 0 4mm;
    color: #ffffff;
    page-break-before: auto;
  }}
  .cover .sub {{
    font-size: 13pt;
    color: #aab6d4;
    margin-bottom: 16mm;
  }}
  .cover img {{
    width: 54mm;
    height: 54mm;
    border-radius: 12mm;
    display: block;
    margin: 18mm 0 12mm;
  }}
  .cover .meta {{
    position: absolute;
    bottom: 22mm;
    left: 20mm;
    right: 20mm;
    display: flex;
    justify-content: space-between;
    color: #9aa5c3;
    font-size: 8.5pt;
    border-top: 0.3mm solid #2a3852;
    padding-top: 5mm;
  }}
  h1 {{
    page-break-before: always;
    font-size: 20pt;
    line-height: 1.35;
    color: #0d1729;
    margin: 0 0 7mm;
    padding-bottom: 3mm;
    border-bottom: 0.5mm solid #d9e2ec;
  }}
  h1:first-of-type {{ page-break-before: auto; }}
  h2 {{
    font-size: 13.5pt;
    color: #15233a;
    margin: 7mm 0 2.5mm;
    page-break-after: avoid;
  }}
  h3 {{
    font-size: 11pt;
    color: #263a55;
    margin: 5mm 0 2mm;
    page-break-after: avoid;
  }}
  p {{ margin: 2mm 0; }}
  .lead {{ color: #53617a; font-size: 10pt; }}
  a {{ color: #0b7d68; text-decoration: none; }}
  blockquote {{
    margin: 4mm 0;
    padding: 3mm 4mm;
    background: #f4f8f7;
    border-left: 1.2mm solid #5bd9bd;
    color: #36465e;
  }}
  code {{
    font-family: Consolas, "Cascadia Mono", monospace;
    background: #f2f5f9;
    color: #a33a5a;
    padding: 0.4mm 1.2mm;
    border-radius: 1mm;
    font-size: 9pt;
  }}
  pre {{
    background: #0d1729;
    color: #dbe8f5;
    padding: 4mm;
    border-radius: 2.5mm;
    overflow: hidden;
    font-size: 8.2pt;
    line-height: 1.55;
    page-break-inside: avoid;
    white-space: pre-wrap;
  }}
  pre code {{ background: transparent; color: inherit; padding: 0; }}
  table {{
    width: 100%;
    border-collapse: collapse;
    margin: 3mm 0 5mm;
    font-size: 9.2pt;
    page-break-inside: avoid;
  }}
  th {{
    background: #eef4f7;
    color: #17253b;
    text-align: left;
    font-weight: 700;
  }}
  th, td {{
    border: 0.25mm solid #d6e0e9;
    padding: 1.8mm 2.2mm;
    vertical-align: top;
  }}
  tr:nth-child(even) td {{ background: #fafcfe; }}
  ul, ol {{ margin: 2mm 0 3mm; padding-left: 6mm; }}
  li {{ margin: 1mm 0; }}
  hr {{
    border: none;
    border-top: 0.3mm solid #dbe3ec;
    margin: 6mm 0;
  }}
  .shots {{
    display: flex;
    flex-wrap: wrap;
    gap: 5mm;
    align-items: flex-start;
  }}
  figure {{
    margin: 0;
    width: 78mm;
    page-break-inside: avoid;
  }}
  figure img {{
    width: 78mm;
    border-radius: 3mm;
    border: 0.3mm solid #dbe3ec;
    display: block;
  }}
  figure.tall {{
    width: 82mm;
  }}
  figure.tall img {{
    width: 82mm;
  }}
  figcaption {{
    color: #6a768b;
    font-size: 8pt;
    text-align: center;
    margin-top: 1.5mm;
  }}
  .cover h1, .cover .sub, .cover .eyebrow {{ page-break-before: avoid; }}
</style>
</head>
<body>
  <section class="cover">
    <div class="eyebrow">AURORA LIGHT · BLE CONTROLLER</div>
    <img src="data:image/png;base64,{icon_b64}" alt="Aurora Light">
    <h1>幻彩灯控<br>使用与协议文档</h1>
    <div class="sub">无注册 · 无登录 · 仅联网检查更新<br>BLE 幻彩灯控制 App v1.1.1</div>
    <div class="meta">
      <span>Android 6.0+ · BLE 4.0+</span>
      <span>文档版本 1.0 · 2026-11</span>
    </div>
  </section>
  {shots_html}
  {body}
</body>
</html>
"""

with open(OUT, "w", encoding="utf-8") as f:
    f.write(html)
print(OUT)

