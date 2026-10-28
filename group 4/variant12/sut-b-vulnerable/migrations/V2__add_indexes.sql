BEGIN;
CREATE INDEX order_items_product_idx ON order_items(product_id);
CREATE INDEX orders_status_id_idx ON orders(status, id);
COMMIT;
