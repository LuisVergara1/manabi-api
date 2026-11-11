-- Artifact history only; no educational changes, activation or public pointer.
CREATE TABLE publication_storage (
 release_id VARCHAR(71) COLLATE "C" PRIMARY KEY REFERENCES publication_candidate(release_id),
 job_id VARCHAR(36) COLLATE "C" NOT NULL REFERENCES publication_job(job_id),
 manifest_sha256 VARCHAR(64) NOT NULL CHECK(manifest_sha256 ~ '^[a-f0-9]{64}$'),
 stored_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE publication_export (
 export_id VARCHAR(36) COLLATE "C" PRIMARY KEY,
 release_id VARCHAR(71) COLLATE "C" NOT NULL REFERENCES publication_storage(release_id),
 zip_sha256 VARCHAR(64) NOT NULL CHECK(zip_sha256 ~ '^[a-f0-9]{64}$'),
 inventory_sha256 VARCHAR(64) NOT NULL CHECK(inventory_sha256 ~ '^[a-f0-9]{64}$'),
 byte_length BIGINT NOT NULL CHECK(byte_length>0),
 storage_key VARCHAR(256) NOT NULL,
 source_releases TEXT NOT NULL,
 exported_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_publication_export_release ON publication_export(release_id,zip_sha256);
CREATE TABLE publication_revocation (
 release_id VARCHAR(71) COLLATE "C" PRIMARY KEY REFERENCES publication_candidate(release_id),
 reason TEXT NOT NULL CHECK(btrim(reason)<>''),
 revoked_at TIMESTAMPTZ NOT NULL
);
