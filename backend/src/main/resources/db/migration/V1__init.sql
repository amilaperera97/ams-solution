CREATE TABLE organisation (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE provider (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (organisation_id) REFERENCES organisation(id)
);

CREATE TABLE environment (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    provider_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    FOREIGN KEY (provider_id) REFERENCES provider(id)
);

CREATE TABLE account (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    provider_id VARCHAR(36) NOT NULL,
    environment_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    auth_type VARCHAR(50) NOT NULL,
    token VARCHAR(255),
    role_arn VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    FOREIGN KEY (provider_id) REFERENCES provider(id),
    FOREIGN KEY (environment_id) REFERENCES environment(id)
);

CREATE TABLE scan (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    scope_type VARCHAR(50) NOT NULL,
    provider_ids TEXT,
    environment_ids TEXT,
    account_ids TEXT,
    regions TEXT,
    services TEXT,
    status VARCHAR(50) NOT NULL,
    progress_percent INT DEFAULT 0,
    accounts_total INT DEFAULT 0,
    accounts_completed INT DEFAULT 0,
    certificates_discovered INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE certificate (
    id VARCHAR(36) PRIMARY KEY,
    scan_id VARCHAR(36) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    environment VARCHAR(255) NOT NULL,
    region VARCHAR(100),
    domain VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    service VARCHAR(100),
    resource VARCHAR(255),
    issued_date TIMESTAMP,
    expiry_date TIMESTAMP,
    issuer VARCHAR(255),
    algorithm VARCHAR(100),
    auto_renewal BOOLEAN,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (scan_id) REFERENCES scan(id)
);
