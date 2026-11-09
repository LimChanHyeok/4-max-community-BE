#!/usr/bin/env python3
"""검색 벤치마크 게시글을 생성, 적재, 검증한다."""

import argparse
from collections import Counter
from datetime import datetime
import json
import os
import random
import sys

BLOCK_SIZE = 1_000
TOTAL = 1_000_000
SEED = 81
START = datetime(2024, 11, 9)
END = datetime(2026, 11, 9)
FREQUENT = "손흥민 토트넘 프리킥 이강인 파리생제르맹 페널티킥 김민재 바이에른뮌헨 코너킥".split()
RARE = "오버래핑 언더래핑 라볼피아나 역압박 게겐프레싱 카테나치오 하프스페이스 인버티드풀백 제로톱".split()
ABSENT = "즐라탄 아약스 라보나".split()
PAIRS = {("손흥민", "골"): 20, ("김민재", "수비"): 20, ("이강인", "도움"): 2}
PERIOD = {"정우영": (0, 200_000), "황희찬": (800_000, TOTAL)}
LENGTHS = ((1, 200, 700), (201, 999, 250), (1_000, 3_000, 50))
MOMENTS = ("전반 시작", "전반 중반", "전반 막판", "후반 시작", "후반 중반", "후반 막판", "추가시간", "교체 직후")
PLAYS = ("왼쪽 측면 돌파", "오른쪽 측면 전개", "중앙 패스", "문전 슈팅", "빠른 역습", "첫 터치",
         "박스 앞 압박", "롱패스", "측면 크로스", "헤더", "볼 탈취", "중앙 돌파")
REACTIONS = ("다시 보니 다르네요", "현장에서는 더 인상적이었어요", "타이밍이 좋았어요", "다음 경기에도 보고 싶네요",
             "한 박자 빨랐어요", "순간 분위기가 바뀌었어요", "중계로 보니 잘 보이네요", "좀 더 지켜봐야겠어요")
DETAILS = ("동료가 바깥으로 빠지면서 안쪽 공간이 열렸어요", "상대가 올라오는 순간 뒤쪽에 공간이 생겼어요",
           "받기 전에 주위를 확인한 게 눈에 들어왔어요", "볼을 넘긴 뒤 움직임이 좋았어요",
           "다음 패스를 기다리며 간격을 유지했어요", "중앙에서 시선을 끈 뒤 측면으로 풀었어요",
           "속도를 줄였다가 다시 올리니 상대가 따라가지 못했어요", "경기 초반보다 선수 간격이 좁아졌어요")


def timestamp(position):
    return START + (END - START) * (position / TOTAL)


def sentence(rng):
    return f"{rng.choice(MOMENTS)} {rng.choice(PLAYS)} 때 {rng.choice(DETAILS)}. {rng.choice(REACTIONS)}."


def generate_block(offset, authors):
    rng = random.Random(SEED + offset)
    rows = list(range(BLOCK_SIZE))
    rng.shuffle(rows)
    groups = [rows[:700], rows[700:950], rows[950:]]
    keywords = [[] for _ in rows]

    # 다중 검색어의 기준 이름을 서로 다른 글에 배치해 동시 일치 수를 정확히 맞춘다.
    for index, ((first, second), pair_count) in enumerate(PAIRS.items()):
        matching = rows[index * 100:(index + 1) * 100]
        for row in matching:
            keywords[row].append(first)
        for row in rng.sample(matching, pair_count):
            keywords[row].append(second)

    for term in FREQUENT:
        if term not in {pair[0] for pair in PAIRS}:
            for row in rng.sample(rows, 100):
                keywords[row].append(term)
    for term in RARE:
        keywords[rng.choice(rows)].append(term)

    position_title = {}
    for term, (start, stop) in PERIOD.items():
        if start <= offset < stop:
            for group, title_quota in zip(groups, (175, 62, 13)):
                matching = rng.sample(group, len(group) // 2)
                for row in matching:
                    keywords[row].append(term)
                    position_title[row] = False
                # 제목 50%를 블록마다 유지하고 짧은 글·일반 글·긴 글의 비율도 맞춘다.
                for row in rng.sample(matching, title_quota):
                    position_title[row] = True

    limits = {row: (low, high) for group, (low, high, _) in zip(groups, LENGTHS) for row in group}
    for row in range(BLOCK_SIZE):
        words = keywords[row].copy()
        title = f"{rng.choice(MOMENTS)} {rng.choice(PLAYS)}"
        if row in position_title:
            marker = next(word for word in words if word in PERIOD)
            if position_title[row]:
                title = f"{marker} {title}"
                words.remove(marker)
        elif words and rng.random() < 0.35:
            title = f"{words.pop(0)} {title}"
        low, high = limits[row]
        keyword_text = " · ".join(f"{word} 장면을 다시 봤어요." for word in words)
        body = (keyword_text + " " if keyword_text else "") + sentence(rng)
        goal = rng.randint(max(low, 45), high)
        while len(body) < goal:
            paragraph = "\n\n" if low >= 1_000 and rng.random() < 0.25 else " "
            body += paragraph + sentence(rng)
        body = body[:high].rstrip()
        assert 5 <= len(title) <= 30 and low <= len(body) <= high, (title, len(body), low, high)
        date = timestamp(offset + row)
        yield rng.choice(authors), title, body, date, date


def expected(target):
    blocks = target // BLOCK_SIZE
    counts = {"게시글": target}
    counts.update({f"본문 {low}~{high}자": quota * blocks for low, high, quota in LENGTHS})
    counts["제목 5~30자"] = target
    counts.update({word: 100 * blocks for word in FREQUENT})
    counts.update({word: blocks for word in RARE})
    counts.update(dict.fromkeys(ABSENT, 0))
    counts.update({" ".join(pair): quota * blocks for pair, quota in PAIRS.items()})
    for word, (start, stop) in PERIOD.items():
        counts[word] = max(0, min(target, stop) - start) // 2
        counts[f"{word} 구간 밖"] = 0
    return counts


def preview(target):
    counts = Counter()
    for offset in range(0, target, BLOCK_SIZE):
        for _, title, body, date, _ in generate_block(offset, range(20)):
            counts["게시글"] += 1
            counts["제목 5~30자"] += 5 <= len(title) <= 30
            for low, high, _ in LENGTHS:
                counts[f"본문 {low}~{high}자"] += low <= len(body) <= high
            for word in FREQUENT + RARE + ABSENT + list(PERIOD):
                counts[word] += word in title or word in body
            for pair in PAIRS:
                counts[" ".join(pair)] += all(word in title or word in body for word in pair)
            for word, (start, stop) in PERIOD.items():
                outside = not timestamp(start) <= date < timestamp(stop)
                counts[f"{word} 구간 밖"] += outside and (word in title or word in body)
    check_counts(counts, target)


def check_counts(actual, target):
    failures = [f"{key}: {actual[key]:,} (예상 {count:,})"
                for key, count in expected(target).items() if actual[key] != count]
    if failures:
        raise RuntimeError("분포 불일치\n" + "\n".join(failures))
    print(json.dumps(dict(actual), ensure_ascii=False, indent=2))


def verify(cursor, authors, target):
    placeholders = ", ".join(["%s"] * len(authors))
    cursor.execute(f"SELECT COUNT(DISTINCT user_id), COUNT(DISTINCT created_at), "
                   f"MIN(created_at), MAX(created_at) FROM posts WHERE user_id IN ({placeholders})", authors)
    if cursor.fetchone() != (len(authors), target, START, timestamp(target - 1)):
        raise RuntimeError("작성자 수 또는 작성 시각 범위가 예상과 다릅니다.")

    expressions = ["COUNT(*)"]
    params = []
    for low, high, _ in LENGTHS:
        expressions.append(f"COALESCE(SUM(CHAR_LENGTH(content) BETWEEN {low} AND {high}), 0)")
    expressions.append("COALESCE(SUM(CHAR_LENGTH(title) BETWEEN 5 AND 30), 0)")
    match = "(title LIKE %s OR content LIKE %s)"
    for words in [(word,) for word in FREQUENT + RARE + ABSENT] + list(PAIRS):
        expressions.append("COALESCE(SUM(" + " AND ".join([match] * len(words)) + "), 0)")
        for word in words:
            params.extend([f"%{word}%"] * 2)
    for word, (start, stop) in PERIOD.items():
        expressions.append(f"COALESCE(SUM({match}), 0)")
        expressions.append(f"COALESCE(SUM({match} AND (created_at < %s OR created_at >= %s)), 0)")
        params.extend([f"%{word}%"] * 4 + [timestamp(start), timestamp(stop)])
    cursor.execute(f"SELECT {', '.join(expressions)} FROM posts WHERE user_id IN ({placeholders})", params + authors)
    check_counts(dict(zip(expected(target), map(int, cursor.fetchone()))), target)


def database(mode, port, target):
    import pymysql

    password = os.environ["BENCHMARK_DB_PASSWORD"]
    connection = pymysql.connect(host="127.0.0.1", port=port, user="community_search",
                                 password=password, database="community_search", charset="utf8mb4",
                                 autocommit=True, connect_timeout=10)
    with connection, connection.cursor() as cursor:
        cursor.execute("SELECT GET_LOCK('community-search-bench81', 0)")
        if cursor.fetchone()[0] != 1:
            raise RuntimeError("다른 벤치마크 적재 또는 검증이 진행 중입니다.")
        cursor.execute("SELECT id, email FROM users WHERE email LIKE 'bench81-___@example.test' ORDER BY email")
        users = cursor.fetchall()
        if [email for _, email in users] != [f"bench81-{n:03d}@example.test" for n in range(1, 21)]:
            raise RuntimeError("벤치마크 작성자 20명을 정확히 찾지 못했습니다.")
        authors = [user_id for user_id, _ in users]
        if mode == "load":
            placeholders = ", ".join(["%s"] * len(authors))
            cursor.execute(f"SELECT COUNT(*), COUNT(DISTINCT created_at), MIN(created_at), MAX(created_at) "
                           f"FROM posts WHERE user_id IN ({placeholders})", authors)
            count, distinct, first, last = cursor.fetchone()
            if count > target or count % BLOCK_SIZE or (count and
                    (distinct != count or first != START or last != timestamp(count - 1))):
                raise RuntimeError("기존 테스트 작성자의 게시글이 예상 누적 구간과 다릅니다.")
            for offset in range(count, target, BLOCK_SIZE):
                connection.begin()
                cursor.executemany(
                    "INSERT INTO posts (user_id, title, content, created_at, updated_at, "
                    "view_count, like_count, comment_count) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
                    [(*row, 0, 0, 0) for row in generate_block(offset, authors)],
                )
                connection.commit()
                print(f"적재 완료 {offset + BLOCK_SIZE:,}/{target:,}", flush=True)
        verify(cursor, authors, target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("preview", "load", "verify"))
    parser.add_argument("--target", type=int, choices=(10_000, 100_000, TOTAL), required=True)
    parser.add_argument("--port", type=int, default=13381, help="로컬 SSM 포트 포워딩 포트")
    args = parser.parse_args()
    if args.mode == "preview":
        preview(args.target)
    else:
        database(args.mode, args.port, args.target)


if __name__ == "__main__":
    main()
