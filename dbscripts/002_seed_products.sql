-- One-time seed data so the catalog is not empty on first load.
-- Run once after 001_create_products.sql.

INSERT INTO products (name, description, price, stock) VALUES
('Wireless Mouse', 'Ergonomic 2.4GHz wireless mouse', 19.99, 150),
('Mechanical Keyboard', 'Tenkeyless mechanical keyboard, brown switches', 89.50, 60),
('USB-C Hub', '7-in-1 hub with HDMI, USB 3.0 and SD slots', 45.00, 200),
('27" 4K Monitor', 'IPS panel, 3840x2160, 60Hz', 329.99, 25),
('Laptop Stand', 'Aluminium adjustable stand', 24.75, 90);
