#!/usr/bin/env python3
"""
상품 테스트 데이터 생성 스크립트 (기본 10만 건)

Prerequisites:
    pip install mysql-connector-python

Usage:
    python3 scripts/seed_products.py
    python3 scripts/seed_products.py --count 200000
    python3 scripts/seed_products.py --host 127.0.0.1 --user root --password secret
"""

import argparse
import random
import datetime
import sys

try:
    import mysql.connector
except ImportError:
    print("mysql-connector-python 패키지가 필요합니다.")
    print("  pip install mysql-connector-python")
    sys.exit(1)


BRAND_NAMES = [
    "나이키", "아디다스", "애플", "삼성", "LG",
    "자라", "H&M", "유니클로", "무인양품", "이케아",
    "발뮤다", "다이슨", "소니", "파나소닉", "보스",
    "네스프레소", "레고", "닌텐도", "루이비통", "구찌",
]

CATEGORIES  = ["의류", "전자기기", "도서", "스포츠용품", "뷰티", "식품", "가구", "완구", "액세서리", "주방용품"]
ADJECTIVES  = ["프리미엄", "슬림", "클래식", "모던", "빈티지", "스마트", "에코", "럭셔리", "미니", "맥스"]


def make_price() -> int:
    """
    1,000 ~ 1,000,000원, 100원 단위.
    저가(60%) / 중가(25%) / 고가(15%) 분포.
    """
    tier = random.random()
    if tier < 0.60:
        return random.randint(10, 500) * 100       # 1,000 ~ 50,000
    elif tier < 0.85:
        return random.randint(500, 3_000) * 100    # 50,000 ~ 300,000
    else:
        return random.randint(3_000, 10_000) * 100 # 300,000 ~ 1,000,000


def make_like_count() -> int:
    """
    지수 분포 (평균 200). 대부분 적고 소수가 많음. 최대 10,000.
    """
    return min(int(random.expovariate(1 / 200)), 10_000)


def insert_brands(cursor, conn) -> list[int]:
    now = datetime.datetime.utcnow()
    cursor.executemany(
        "INSERT IGNORE INTO brands (name, description, created_at, updated_at) VALUES (%s, %s, %s, %s)",
        [(name, f"{name} 공식 스토어", now, now) for name in BRAND_NAMES],
    )
    conn.commit()

    placeholders = ",".join(["%s"] * len(BRAND_NAMES))
    cursor.execute(f"SELECT id FROM brands WHERE name IN ({placeholders})", BRAND_NAMES)
    brand_ids = [row[0] for row in cursor.fetchall()]
    print(f"브랜드 {len(brand_ids)}개 준비 완료")
    return brand_ids


def insert_products_and_stocks(cursor, conn, brand_ids: list[int], total: int, batch_size: int):
    now = datetime.datetime.utcnow()
    inserted = 0

    for batch_start in range(0, total, batch_size):
        count = min(batch_size, total - batch_start)
        product_rows = []

        for j in range(count):
            i        = batch_start + j + 1
            brand_id = random.choice(brand_ids)
            name     = f"{random.choice(ADJECTIVES)} {random.choice(CATEGORIES)} {i:06d}"
            price      = make_price()
            like_count = make_like_count()

            # created_at: 최근 2년 내 랜덤
            days_ago   = random.randint(0, 730)
            secs_ago   = random.randint(0, 86_400)
            created_at = now - datetime.timedelta(days=days_ago, seconds=secs_ago)

            # 5% 확률로 삭제 처리
            deleted_at = created_at if random.random() < 0.05 else None

            product_rows.append((brand_id, name, price, like_count, created_at, created_at, deleted_at))

        cursor.executemany(
            "INSERT INTO products (brand_id, name, price, like_count, created_at, updated_at, deleted_at) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s)",
            product_rows,
        )
        # executemany 후 lastrowid = 이 배치의 첫 번째 auto_increment ID
        first_id = cursor.lastrowid

        stock_rows = [
            (first_id + j, random.randint(0, 500), now, now)
            for j in range(count)
        ]
        cursor.executemany(
            "INSERT INTO stocks (product_id, quantity, created_at, updated_at) VALUES (%s, %s, %s, %s)",
            stock_rows,
        )
        conn.commit()

        inserted += count
        if inserted % 10_000 == 0 or inserted == total:
            print(f"  {inserted:,} / {total:,} 완료")


def main():
    parser = argparse.ArgumentParser(description="상품 테스트 데이터 생성")
    parser.add_argument("--host",     default="localhost")
    parser.add_argument("--port",     type=int, default=3306)
    parser.add_argument("--user",     default="application")
    parser.add_argument("--password", default="application")
    parser.add_argument("--db",       default="loopers")
    parser.add_argument("--count",    type=int, default=100_000, help="삽입할 상품 수 (기본: 100,000)")
    parser.add_argument("--batch",    type=int, default=1_000,   help="배치 크기 (기본: 1,000)")
    args = parser.parse_args()

    print(f"DB: {args.user}@{args.host}:{args.port}/{args.db}")

    conn = mysql.connector.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.db,
    )
    cursor = conn.cursor()

    try:
        brand_ids = insert_brands(cursor, conn)

        print(f"\n상품 {args.count:,}개 삽입 중 (배치: {args.batch:,})...")
        insert_products_and_stocks(cursor, conn, brand_ids, args.count, args.batch)

        cursor.execute("SELECT COUNT(*) FROM products")
        total_products = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM stocks")
        total_stocks = cursor.fetchone()[0]

        print(f"\n완료")
        print(f"  products: {total_products:,}건")
        print(f"  stocks  : {total_stocks:,}건")
    finally:
        cursor.close()
        conn.close()


if __name__ == "__main__":
    main()
