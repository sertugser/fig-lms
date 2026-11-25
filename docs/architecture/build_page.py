#!/usr/bin/env python3
"""Builds the offline presentation page from the diagram files in this folder.

Reads 01-moodle.md, 02-yeni-sistem.md and 03-karsilastirma.md, takes every "## Diyagram N: ..." section
(its mermaid block, its "Bunu şöyle anlat" quote, its "Kanıt" list and any text code block), renders the
mermaid code to SVG in a headless browser and writes one self-contained HTML file (no internet needed).

Needs Python 3, Node.js with Playwright (Chromium) and the mermaid npm package:
    npm install --prefix /tmp/mermaid mermaid@11.4.1
    python3 docs/architecture/build_page.py /tmp/mermaid/node_modules/mermaid/dist/mermaid.min.js \
        docs/presentation/2026-12-week2-mimari/index.html

The .md files are the source. Edit them, then run this script again.
"""
import html
import json
import pathlib
import re
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
SOURCES = [
    ("eski", "Eski sistem: Moodle 5.3.1", HERE / "01-moodle.md"),
    ("yeni", "Yeni sistem: Fig LMS", HERE / "02-yeni-sistem.md"),
    ("karsilastirma", "Yan yana", HERE / "03-karsilastirma.md"),
]


def inline(md):
    text = html.escape(md, quote=False)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1", text)
    return text


def sections(path):
    body = path.read_text(encoding="utf-8")
    parts = re.split(r"^## ", body, flags=re.M)[1:]
    for part in parts:
        title, _, rest = part.partition("\n")
        if not title.startswith("Diyagram"):
            continue
        number = int(re.match(r"Diyagram (\d+)", title).group(1))
        mermaid = re.search(r"```mermaid\n(.*?)```", rest, re.S).group(1)
        quote_match = re.search(r"\*\*Bunu şöyle anlat:\*\*\n((?:>.*\n?)+)", rest)
        quote = []
        if quote_match:
            for para in re.split(r"\n>\s*\n", quote_match.group(1)):
                lines = [l[1:].strip() for l in para.splitlines() if l.startswith(">")]
                if lines:
                    quote.append(" ".join(lines))
        outputs = re.findall(r"```text\n(.*?)```", rest, re.S)
        evidence = []
        ev_match = re.search(r"\*\*Kanıt[^*]*\*\*(.*?)(?:\n---|\Z)", rest, re.S)
        if ev_match:
            evidence = [l[2:].strip() for l in ev_match.group(1).splitlines() if l.startswith("- ")]
        yield {
            "number": number,
            "title": re.sub(r"^Diyagram \d+:\s*", "", title).strip(),
            "mermaid": mermaid,
            "quote": quote,
            "outputs": outputs,
            "evidence": evidence,
        }


def render_svgs(mermaid_js, codes):
    with tempfile.TemporaryDirectory() as tmp:
        src, dst = pathlib.Path(tmp, "in.json"), pathlib.Path(tmp, "out.json")
        src.write_text(json.dumps(codes), encoding="utf-8")
        subprocess.run(["node", str(HERE / "render_mermaid.js"), mermaid_js, str(src), str(dst)], check=True)
        return json.loads(dst.read_text(encoding="utf-8"))


def main(mermaid_js, out_file):
    groups = [(key, label, list(sections(path))) for key, label, path in SOURCES]
    diagrams = [d for _, _, ds in groups for d in ds]
    svgs = render_svgs(mermaid_js, [d["mermaid"] for d in diagrams])
    for d, svg in zip(diagrams, svgs):
        d["svg"] = svg

    template = (HERE / "page-template.html").read_text(encoding="utf-8")
    nav, content = [], []
    for key, label, ds in groups:
        content.append(f'<section class="part part-{key}" id="{key}"><h2 class="part-title">{html.escape(label)}</h2>')
        for d in ds:
            anchor = f"d{d['number']}"
            nav.append(f'<a href="#{anchor}" class="nav-{key}"><span>{d["number"]}</span>{html.escape(d["title"])}</a>')
            block = [f'<article class="diagram" id="{anchor}">',
                     f'<header><span class="num">Diyagram {d["number"]}</span><h3>{inline(d["title"])}</h3></header>',
                     f'<div class="board">{d["svg"]}</div>']
            if d["quote"]:
                block.append('<div class="say"><p class="say-label">Bunu şöyle anlat</p>'
                             + "".join(f"<p>{inline(q)}</p>" for q in d["quote"]) + "</div>")
            for output in d["outputs"]:
                block.append('<div class="output"><p class="say-label">Gerçek çıktı (25 Kasım 2026)</p>'
                             f"<pre>{html.escape(output)}</pre></div>")
            if d["evidence"]:
                block.append('<details class="evidence"><summary>Kanıt: nereden biliyoruz?</summary><ul>'
                             + "".join(f"<li>{inline(e)}</li>" for e in d["evidence"]) + "</ul></details>")
            block.append("</article>")
            content.append("\n".join(block))
        content.append("</section>")

    page = template.replace("{{NAV}}", "\n".join(nav)).replace("{{CONTENT}}", "\n".join(content))
    out = pathlib.Path(out_file)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print(f"{out}: {len(diagrams)} diagrams, {len(page) // 1024} KB")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
