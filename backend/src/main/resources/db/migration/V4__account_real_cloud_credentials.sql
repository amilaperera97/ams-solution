-- Credentials needed to reach a real cloud account.
-- secret_access_key holds ciphertext (prefix "enc:v1:") whenever a secret key is
-- configured; the token column follows the same convention from now on.
ALTER TABLE account ADD COLUMN access_key_id VARCHAR(128);
ALTER TABLE account ADD COLUMN secret_access_key VARCHAR(1024);
ALTER TABLE account ADD COLUMN region VARCHAR(64);
ALTER TABLE account ADD COLUMN external_id VARCHAR(255);
