DROP TABLE IF EXISTS products;
CREATE TABLE products (id BIGINT PRIMARY KEY, brand_id BIGINT NOT NULL, name VARCHAR(80) NOT NULL, price BIGINT NOT NULL);
SET SESSION cte_max_recursion_depth=10000;
INSERT INTO products(id,brand_id,name,price) WITH RECURSIVE seq AS (SELECT 1 id UNION ALL SELECT id+1 FROM seq WHERE id<10000) SELECT id,(id%100)+1,CONCAT('product-',LPAD(id,5,'0')),((id*37)%100000)+100 FROM seq;
