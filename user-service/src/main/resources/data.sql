-- src/main/resources/data.sql

-- Insert initial roles if they do not already exist.
-- This pattern is highly compatible across H2, MySQL, PostgreSQL, SQL Server, and Oracle.

INSERT INTO roles (name)
SELECT 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

INSERT INTO roles (name)
SELECT 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');

INSERT INTO roles (name)
SELECT 'ROLE_SELLER'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_SELLER');

INSERT INTO roles (name)
SELECT 'ROLE_DELIVERY_AGENT'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_DELIVERY_AGENT');