#!/usr/bin/env python3
"""Refresh the built-in Netflix picks from the official weekly chart."""
import csv
from html.parser import HTMLParser
import io
import json
from pathlib import Path
import re
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/assets/top-shelf.json'
TOP10 = 'https://www.netflix.com/tudum/top10'
MAX_PAGE = 6 * 1024 * 1024


def fetch(url, limit):
    request = Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Accept-Language': 'en-US'})
    with urlopen(request, timeout=15) as response:
        if urlsplit(response.geturl()).hostname != 'www.netflix.com':
            raise ValueError('Netflix redirected off its official site')
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError('Netflix response exceeded the size limit')
    return data.decode('utf-8')


class Chart(HTMLParser):
    def __init__(self, page):
        super().__init__()
        self.page = page
        self.image = None
        self.logo = False
        self.entries = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get('data-uia') == 'top10-card':
            match = re.search(r'background-image:\s*url\(([^)]+)\)', attrs.get('style', ''))
            self.image = match.group(1) if match else None
            self.logo = False
        elif attrs.get('data-uia') == 'top10-card-logo':
            self.logo = True
        elif tag == 'img' and self.logo:
            self.logo = False
            title = attrs.get('alt', '').strip()
            url = urlsplit(self.image or '')
            if title and url.scheme == 'https' and url.hostname == 'dnm.nflximg.net':
                self.entries[title] = {'title': title, 'image': self.image, 'page': self.page}


def weekly_picks(kind, rows):
    page = TOP10 if kind == 'Films' else TOP10 + '/tv'
    chart = Chart(page)
    chart.feed(fetch(page, MAX_PAGE))
    picks = []
    for row in rows:
        if not row['category'].startswith(kind):
            continue
        for title in (row['season_title'], row['show_title']):
            if title in chart.entries:
                picks.append(chart.entries[title])
                break
        if len(picks) == 2:
            break
    if len(picks) != 2:
        raise ValueError(f'Could not match the official {kind} weekly chart with two cover images')
    return picks


def main():
    data = fetch(TOP10 + '/data/all-weeks-global.tsv', 2 * 1024 * 1024)
    rows = list(csv.DictReader(io.StringIO(data), delimiter='\t'))
    week = max(row['week'] for row in rows)
    latest = sorted((row for row in rows if row['week'] == week), key=lambda row: int(row['weekly_rank']))
    movies = weekly_picks('Films', latest)
    shows = weekly_picks('TV', latest)
    catalog = json.loads(CATALOG.read_text(encoding='utf-8'))
    catalog['NETFLIX'] = [movies[0], shows[0], movies[1], shows[1]]
    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f'Netflix {week}: ' + ', '.join(item['title'] for item in catalog['NETFLIX']))


if __name__ == '__main__':
    main()
