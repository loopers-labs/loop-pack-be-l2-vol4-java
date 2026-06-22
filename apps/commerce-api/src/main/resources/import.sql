-- ── users unique (활성 계정만) ──────────────────────────────────────────
ALTER TABLE users ADD COLUMN login_id_unique_key VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, login_id, NULL)) VIRTUAL;
CREATE UNIQUE INDEX uk_users_login_id_active ON users (login_id_unique_key);
ALTER TABLE users ADD COLUMN email_unique_key VARCHAR(255) GENERATED ALWAYS AS (IF(deleted_at IS NULL, email, NULL)) VIRTUAL;
CREATE UNIQUE INDEX uk_users_email_active ON users (email_unique_key);

-- ── likes unique (중복 좋아요 방지) ──────────────────────────────────────
CREATE UNIQUE INDEX uk_likes_member_product ON likes (member_id, product_id);

-- ── products 조회 인덱스 ──────────────────────────────────────────────────
CREATE INDEX idx_products_brand_id          ON products (brand_id);
CREATE INDEX idx_products_brand_created     ON products (brand_id, created_at DESC);
CREATE INDEX idx_products_brand_price       ON products (brand_id, price ASC);

-- ── likes 조회 인덱스 ────────────────────────────────────────────────────
CREATE INDEX idx_likes_member_created       ON likes (member_id, created_at DESC);

-- ── orders 조회 인덱스 ───────────────────────────────────────────────────
CREATE INDEX idx_orders_member_created      ON orders (member_id, created_at DESC);

-- ── order_items 조회 인덱스 ─────────────────────────────────────────────
CREATE INDEX idx_order_items_order_product  ON order_items (order_id, product_id);

-- ── product_like_view 인덱스 ─────────────────────────────────────────────
CREATE INDEX idx_plv_like_count ON product_like_view (like_count DESC, product_id);

-- ── deleted_at 인덱스 (@SQLRestriction deleted_at IS NULL 조건) ──────────
CREATE INDEX idx_users_deleted_at           ON users (deleted_at);
CREATE INDEX idx_brands_deleted_at          ON brands (deleted_at);
CREATE INDEX idx_products_deleted_at        ON products (deleted_at);
CREATE INDEX idx_stocks_deleted_at          ON stocks (deleted_at);
CREATE INDEX idx_orders_deleted_at          ON orders (deleted_at);
