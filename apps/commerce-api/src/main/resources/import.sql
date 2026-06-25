ALTER TABLE users ADD COLUMN login_id_unique_key VARCHAR(20) GENERATED ALWAYS AS (IF(deleted_at IS NULL, login_id, NULL)) VIRTUAL;
CREATE UNIQUE INDEX uk_users_login_id_active ON users (login_id_unique_key);
ALTER TABLE users ADD COLUMN email_unique_key VARCHAR(255) GENERATED ALWAYS AS (IF(deleted_at IS NULL, email, NULL)) VIRTUAL;
CREATE UNIQUE INDEX uk_users_email_active ON users (email_unique_key);
