# W4 read optimization decision

The frozen 10,000-row seed reconciles to total `10000/472685000` and brand 7 `100/4647200`. The public query is DB-authoritative, ordered by `(price,id)`, limited to 20, and cache-aside. Its canonical compact `[{"id":n,"price":n}]` SHA-256 is `46c0bdfab8b815a0bef93fd4b09ebbe37e45ae79a61279e6ac7d2792d544ee02` before and after the index.

`ProductQueryE2ETest` captures both raw `EXPLAIN ANALYZE` strings. The baseline proves an executed 20-row limit; after `ANALYZE TABLE`, the plan names `idx_products_brand_price_id` while the response checksum remains identical. Price changes evict the brand keyspace; Redis read/write/eviction failure is contained and falls back to the same DB result. A deliberate cache-exception propagation made `ProductCacheFailureTest` Red in both tracks before restoration.
