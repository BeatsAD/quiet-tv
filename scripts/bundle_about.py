#!/usr/bin/env python3
"""Package the reviewed About screen verbatim, without prototype review tools."""
from html import escape
from html.parser import HTMLParser
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]


class AboutSection(HTMLParser):
    def __init__(self, source):
        super().__init__()
        self.lines = source.splitlines(keepends=True)
        self.start = self.end = None
        self.depth = 0
        self.feed(source)

    def source_offset(self):
        line, column = self.getpos()
        return sum(map(len, self.lines[:line - 1])) + column

    def handle_starttag(self, tag, attrs):
        if tag == 'section':
            if dict(attrs).get('id') == 'about':
                self.start = self.source_offset()
                self.depth = 1
            elif self.depth:
                self.depth += 1

    def handle_endtag(self, tag):
        if tag == 'section' and self.depth:
            self.depth -= 1
            if self.depth == 0:
                self.end = self.source_offset() + len('</section>')


def bundle(output):
    output.mkdir(parents=True, exist_ok=True)
    prototype = ROOT / 'prototypes/about'
    source = (prototype / 'index.html').read_text()
    section = AboutSection(source)
    if section.start is None or section.end is None:
        raise ValueError('Reviewed About section is missing')
    screen = source[section.start:section.end]
    screen = screen.replace('href="../../LICENSE"', 'href="license.html"')
    screen = screen.replace('target="_blank"', 'target="_self"')
    document = ('<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">'
                '<meta name="viewport" content="width=device-width, initial-scale=1">'
                '<title>关于 Quiet TV</title><link rel="stylesheet" href="styles.css"></head>'
                '<body class="embedded"><main id="viewport"><div id="tv">' + screen +
                '</div></main><script src="app.js"></script></body></html>')
    (output / 'index.html').write_text(document)
    css = (prototype / 'styles.css').read_text()
    css = css.replace('../../app/src/main/assets/fonts/outfit.ttf', 'fonts/outfit.ttf')
    css += '\n.embedded{overflow:hidden}.embedded #viewport{width:100vw!important;height:100vh!important;margin:0;border:0;border-radius:0;box-shadow:none}\n'
    (output / 'styles.css').write_text(css)
    shutil.copyfile(prototype / 'app.js', output / 'app.js')
    (output / 'assets').mkdir(exist_ok=True)
    shutil.copyfile(prototype / 'assets/source.svg', output / 'assets/source.svg')
    shutil.copyfile(prototype / 'assets/wechat.webp', output / 'assets/wechat.webp')
    shutil.copyfile(prototype / 'assets/qq-group.webp', output / 'assets/qq-group.webp')
    # Remove only the obsolete generated contact sheet from earlier builds.
    if (output / 'contact.png').is_file():
        (output / 'contact.png').unlink()
    (output / 'fonts').mkdir(exist_ok=True)
    shutil.copyfile(ROOT / 'app/src/main/assets/fonts/outfit.ttf', output / 'fonts/outfit.ttf')
    notices = [ROOT / 'LICENSE', ROOT / 'THIRD_PARTY_NOTICES.md', ROOT / 'app/src/main/assets/fonts/OFL.txt']
    notices += sorted((ROOT / 'app/src/main/assets/licenses').iterdir())
    text = ''.join('<h2>' + escape(path.name) + '</h2><pre>' + escape(path.read_text()) + '</pre>' for path in notices if path.is_file())
    license_page = ('<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">'
                    '<meta name="viewport" content="width=device-width, initial-scale=1">'
                    '<title>开源许可</title><style>body{margin:5vw;background:#0b121c;color:#dbe5f4;font:1.25vw/1.7 sans-serif}a{color:inherit;display:inline-block;padding:6px 12px;border-radius:20px}a:focus{background:#353a43;outline:2px solid white;outline-offset:3px;box-shadow:0 0 0 3px #101116}a:active{background:#262b33}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:1.05vw/1.7 monospace}h1{font-size:2vw}h2{font-size:1.4vw}</style></head>'
                    '<body><a href="index.html" autofocus>← 返回关于</a><h1>开源许可与致谢</h1>' + text + '</body></html>')
    (output / 'license.html').write_text(license_page)
    print(f'About screen and complete offline licenses bundled: {output}')


if __name__ == '__main__':
    bundle(Path(sys.argv[1]).resolve())
