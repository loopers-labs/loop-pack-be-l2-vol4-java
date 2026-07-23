INSERT INTO product_metrics (
    metric_date,
    product_id,
    view_count,
    like_count,
    order_count,
    order_quantity,
    order_amount,
    created_at,
    updated_at,
    deleted_at
)
WITH RECURSIVE digits (digit) AS (
    SELECT 0
    UNION ALL
    SELECT digit + 1
    FROM digits
    WHERE digit < 9
),
numbers (number_value) AS (
    SELECT
        ones.digit
        + tens.digit * 10
        + hundreds.digit * 100
        + thousands.digit * 1000
        + ten_thousands.digit * 10000
        + hundred_thousands.digit * 100000
        + 1
    FROM digits AS ones
    CROSS JOIN digits AS tens
    CROSS JOIN digits AS hundreds
    CROSS JOIN digits AS thousands
    CROSS JOIN digits AS ten_thousands
    CROSS JOIN digits AS hundred_thousands
)
SELECT
    DATE_SUB(@target_date, INTERVAL @day_offset DAY),
    @product_id_base + number_value,
    MOD(number_value * 17 + @day_offset * 13 + @data_seed, 10000),
    MOD(number_value * 31 + @day_offset * 7 + @data_seed, 1000),
    MOD(number_value * 47 + @day_offset * 5 + @data_seed, 100),
    MOD(number_value * 53 + @day_offset * 3 + @data_seed, 1000),
    MOD(number_value * 97 + @day_offset * 11 + @data_seed, 1000000),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    NULL
FROM numbers
WHERE number_value <= @daily_products;
