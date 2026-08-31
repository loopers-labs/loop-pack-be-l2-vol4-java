# W4 read optimization decision

The frozen 10,000-row seed reconciles to total `10000/472685000` and brand 7 `100/4647200`. The public query is DB-authoritative, ordered by `(price,id)`, limited to 20, and cache-aside. Price changes evict the brand keyspace; Redis read/write/eviction failure is contained and falls back to the same DB result. The required index is `(brand_id,price,id)`. A deliberate cache-exception propagation made `ProductCacheFailureTest` Red in both tracks before restoration.
