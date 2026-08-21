-- One-time seed data so the orders view is not empty on first load.
-- Run once after 003_create_orders_db.sql.

USE ordersdb;

INSERT INTO orders (customer_name, product_name, quantity, total_amount, status) VALUES
('Ali Raza', 'Wireless Mouse', 2, 39.98, 'SHIPPED'),
('Sana Khan', 'Mechanical Keyboard', 1, 89.50, 'PENDING'),
('Usman Tariq', 'USB-C Hub', 3, 135.00, 'DELIVERED'),
('Ayesha Malik', '27" 4K Monitor', 1, 329.99, 'PENDING'),
('Hamza Sheikh', 'Laptop Stand', 2, 49.50, 'CANCELLED');
