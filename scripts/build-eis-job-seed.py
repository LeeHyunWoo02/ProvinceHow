#!/usr/bin/env python3
"""EIS 고용행정통계 엑셀 → 시군구×직종대분류×월 CSV.

입력: data/raw/eis/eis-job-*.xlsx      (구인인원/구직건수/취업건수)
      data/raw/eis/eis-valid-*.xlsx    (유효구인인원/유효구직자수)
출력: data/static/eis_job_stats.csv

명세는 docs/eis-job-statistics.md 참조. 표준 라이브러리만 쓴다(openpyxl 불필요).
"""
import csv
import io
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path
from xml.etree.ElementTree import iterparse

NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / 'data' / 'raw' / 'eis'
OUT = ROOT / 'data' / 'static' / 'eis_job_stats.csv'

# EIS 직종_중분류 → level_top.csv 대분류 코드. docs/eis-job-statistics.md §5-3.
# 2018판·2025판 이름을 모두 넣어 §5-4 정규화를 겸한다.
JOB_TOP = {
    '관리직(임원·부서장)': '01', '경영·행정·사무직': '01', '금융·보험직': '01',
    '인문·사회과학 연구직': '02', '자연·생명과학 연구직': '02',
    '정보통신 연구개발직 및 공학기술직': '02',
    '건설·채굴 연구개발직 및 공학기술직': '02', '제조 연구개발직 및 공학기술직': '02',
    '교육직': '03', '법률직': '03', '사회복지·종교직': '03',
    '경찰·소방·교도직': '03', '군인': '03',
    '보건·의료직': '04',
    '예술·디자인·방송직': '05', '스포츠·레크리에이션직': '05',
    '미용·예식 서비스직': '06', '미용·예식 및 반려동물 서비스직': '06',
    '여행·숙박·오락 서비스직': '06', '음식 서비스직': '06', '경호·경비직': '06',
    '돌봄 서비스직(간병·육아)': '06', '청소 및 기타 개인서비스직': '06',
    '영업·판매직': '07', '운전·운송직': '07',
    '건설·채굴직': '08',
    '기계 설치·정비·생산직': '09',
    '금속·재료 설치·정비·생산직(판금·단조·주조·용접·도장 등)': '09',
    '전기·전자 설치·정비·생산직': '10', '정보통신 설치·정비직': '10',
    '화학·환경 설치·정비·생산직': '11', '섬유·의복 생산직': '11',
    '식품가공·생산직': '11', '식품 가공·생산직': '11',
    '인쇄·목재·공예 및 기타 설치·정비·생산직': '12', '제조 단순직': '12',
    '농림어업직': '13',
}
# 13종 대분류에 자리가 없다. 임의 배정하지 않고 버린다.
JOB_EXCLUDED = {'분류불능'}

# EIS 시도명 → sido.csv 후보. 2026-07-01 통합으로 EIS 는 하나로 부르는데 시드는 둘이다.
SIDO_ALIAS = {
    '전남광주통합특별시': ['광주광역시', '전라남도'],
    '제주도': ['제주특별자치도'],
}
# EIS 시군구 표기 보정. '제주도시' 는 EIS 쪽 오기다(제주시).
SIGUNGU_ALIAS = {('제주특별자치도', '제주도시'): '제주시'}
# 시도 접두사 없이 단독으로 오는 단층제 지역.
SINGLE_TIER = {'세종특별자치시': ('세종특별자치시', '세종특별자치시')}
# 지역이 특정되지 않은 행. 직종의 분류불능과 같은 취지로 버린다.
REGION_EXCLUDED = {'분류불능'}

MEASURES = {
    '구인인원(월)': 'job_openings', '구직건수(월)': 'job_seekers',
    '취업건수(월)': 'placements',
    '유효구인인원(전체)': 'valid_openings', '유효구직자수(전체)': 'valid_seekers',
}
COLUMNS = ['job_openings', 'job_seekers', 'placements', 'valid_openings', 'valid_seekers']


def col_num(ref):
    n = 0
    for ch in re.match(r'([A-Z]+)', ref).group(1):
        n = n * 26 + ord(ch) - 64
    return n


def read_sheet(path):
    """엑셀 첫 시트를 {행번호: {열번호: 값}} 으로 읽는다."""
    z = zipfile.ZipFile(path)
    shared = [re.sub(r'<[^>]+>', '', m) for m in
              re.findall(r'<si>(.*?)</si>', z.read('xl/sharedStrings.xml').decode('utf-8'), re.S)]
    sheet = next(n for n in z.namelist() if n.startswith('xl/worksheets/sheet'))
    rows = {}
    for _, el in iterparse(io.BytesIO(z.read(sheet))):
        if el.tag != NS + 'row':
            continue
        cells = {}
        for c in el.findall(NS + 'c'):
            v = c.find(NS + 'v')
            if v is None:
                continue
            cells[col_num(c.get('r'))] = shared[int(v.text)] if c.get('t') == 's' else v.text
        rows[int(el.get('r'))] = cells
        el.clear()
    return rows


def locate_header(rows):
    """(월 라벨 행, 측정값 이름 행) 을 찾는다. 파일마다 위치가 다를 수 있다."""
    month_row = measure_row = None
    for r in sorted(rows):
        vals = [v for v in rows[r].values() if isinstance(v, str)]
        if month_row is None and any(re.fullmatch(r'\d{4}년 \d{2}월', v) for v in vals):
            month_row = r
        elif month_row is not None and any(v in MEASURES for v in vals):
            measure_row = r
            break
    if month_row is None or measure_row is None:
        raise SystemExit(f'헤더 행을 찾지 못했다 (month={month_row}, measure={measure_row})')
    return month_row, measure_row


def build_column_map(rows, month_row, measure_row):
    """열번호 → (YYYY-MM, 측정값키). 월 라벨은 병합돼 첫 열에만 있으므로 이월시킨다."""
    months = {c: v for c, v in rows[month_row].items()
              if isinstance(v, str) and re.fullmatch(r'\d{4}년 \d{2}월', v)}
    out, current = {}, None
    for c in sorted(rows[measure_row]):
        if c in months:
            current = months[c]
        name = rows[measure_row][c]
        if current and name in MEASURES:
            y, m = re.match(r'(\d{4})년 (\d{2})월', current).groups()
            out[c] = (f'{y}-{m}', MEASURES[name])
    return out


def parse_file(path, acc, stats):
    rows = read_sheet(path)
    month_row, measure_row = locate_header(rows)
    colmap = build_column_map(rows, month_row, measure_row)

    sido = sigungu = None
    for r in sorted(rows):
        if r <= measure_row:
            continue
        cells = rows[r]
        # 행 라벨은 값이 바뀔 때만 기록된다. 직전 값을 이월한다.
        a, b, c = cells.get(1), cells.get(2), cells.get(3)
        if isinstance(a, str) and not a.endswith(' 전체') and a != '총계':
            sido = a
        if isinstance(b, str) and not b.endswith(' 전체'):
            sigungu = b
        if not isinstance(c, str):
            continue  # 총계·시도소계·시군구소계 행에는 직종이 없다
        m = re.fullmatch(r'(\d{4})직종_(.+)', c)
        if not m:
            continue
        job_name = m.group(2)
        if job_name in JOB_EXCLUDED:
            stats['excluded_job'] += 1
            continue
        top = JOB_TOP.get(job_name)
        if top is None:
            stats['unknown_job'].add(job_name)
            continue
        if sigungu is None:
            stats['no_region'] += 1
            continue
        for col, (ym, key) in colmap.items():
            raw = cells.get(col)
            if raw is None:
                continue
            try:
                acc[(sigungu, top, ym)][key] += float(raw)
            except ValueError:
                stats['bad_value'] += 1
        stats['rows'] += 1


def load_sigungu_index():
    """정규화된 (시도, 시군구) → (sigungu_code, sido_code, 표기명)."""
    sido = {}
    with open(ROOT / 'data' / 'static' / 'sido.csv', encoding='utf-8-sig') as f:
        for row in csv.DictReader(f):
            sido[row['name']] = row['sido_code']
    idx = {}
    with open(ROOT / 'data' / 'static' / 'sigungu.csv', encoding='utf-8-sig') as f:
        for row in csv.DictReader(f):
            for name, code in sido.items():
                if code == row['sido_code']:
                    idx[(name, norm(row['name']))] = (row['sigungu_code'], code, row['name'])
    return idx


def norm(s):
    return re.sub(r'\s+', '', s)


def resolve(full_name, idx):
    """'전남광주통합특별시 동구' → ('29110','29','동구'). 못 찾으면 None."""
    if full_name in REGION_EXCLUDED:
        return None
    if full_name in SINGLE_TIER:
        return idx.get((SINGLE_TIER[full_name][0], norm(SINGLE_TIER[full_name][1])))
    parts = full_name.split(' ', 1)
    if len(parts) != 2:
        return None
    eis_sido, rest = parts
    for cand in SIDO_ALIAS.get(eis_sido, [eis_sido]):
        rest2 = SIGUNGU_ALIAS.get((cand, rest), rest)
        hit = idx.get((cand, norm(rest2)))
        if hit:
            return hit
    return None


def main():
    files = sorted(RAW.glob('eis-*.xlsx'))
    if not files:
        raise SystemExit(f'입력 파일이 없다: {RAW}')
    acc = defaultdict(lambda: defaultdict(float))
    stats = {'rows': 0, 'excluded_job': 0, 'unknown_job': set(), 'no_region': 0,
             'bad_value': 0, 'excluded_region': 0}
    for f in files:
        parse_file(f, acc, stats)
        print(f'  읽음: {f.name}')

    idx = load_sigungu_index()
    unresolved = defaultdict(int)
    out_rows = []
    for (sigungu, top, ym), vals in acc.items():
        if sigungu in REGION_EXCLUDED:
            stats['excluded_region'] += 1
            continue
        hit = resolve(sigungu, idx)
        if hit is None:
            unresolved[sigungu] += 1
            continue
        code, sido_code, name = hit
        out_rows.append([code, sido_code, name, top, ym] +
                        [int(vals.get(k, 0)) for k in COLUMNS])
    out_rows.sort(key=lambda r: (r[4], r[0], r[3]))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='') as f:
        w = csv.writer(f)
        w.writerow(['sigungu_code', 'sido_code', 'sigungu_name', 'job_top_code', 'year_month'] + COLUMNS)
        w.writerows(out_rows)

    print(f'\n출력: {OUT.relative_to(ROOT)}  ({len(out_rows):,}행)')
    print(f'  읽은 데이터 행: {stats["rows"]:,}')
    print(f'  분류불능 제외: 직종 {stats["excluded_job"]:,}행 / 지역 {stats["excluded_region"]:,}건')
    if stats['unknown_job']:
        print(f'  !! 매핑 없는 직종 {len(stats["unknown_job"])}종: {sorted(stats["unknown_job"])}')
    if stats['bad_value']:
        print(f'  !! 숫자 파싱 실패 셀: {stats["bad_value"]:,}')
    if unresolved:
        print(f'  !! 매칭 실패 시군구 {len(unresolved)}개:')
        for k in sorted(unresolved):
            print(f'       {k}')
    return 1 if (unresolved or stats['unknown_job']) else 0


if __name__ == '__main__':
    sys.exit(main())
