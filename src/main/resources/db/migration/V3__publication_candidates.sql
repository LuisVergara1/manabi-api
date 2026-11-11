-- Private candidate metadata only: no educational blobs, activation or artifact storage.
CREATE TABLE publication_candidate (
 release_id VARCHAR(71) COLLATE "C" PRIMARY KEY CHECK(release_id ~ '^sha256:[a-f0-9]{64}$'),
 namespace VARCHAR(120) NOT NULL CHECK(namespace='manabi.frontend.v1'),
 manifest_sha256 VARCHAR(64) NOT NULL CHECK(manifest_sha256 ~ '^[a-f0-9]{64}$'),
 minimum_reader_version INTEGER NOT NULL CHECK(minimum_reader_version IN (1,3)),
 created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE publication_package (
 release_id VARCHAR(71) COLLATE "C" NOT NULL REFERENCES publication_candidate(release_id),
 package_id VARCHAR(128) COLLATE "C" NOT NULL,
 sha256 VARCHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 byte_length BIGINT NOT NULL CHECK(byte_length>0),
 record_count BIGINT NOT NULL CHECK(record_count>=0),
 schema_version INTEGER NOT NULL CHECK(schema_version IN (1,2)),
 PRIMARY KEY(release_id,package_id)
);
CREATE INDEX ix_publication_package_hash ON publication_package(package_id,sha256);
CREATE TABLE publication_job (
 job_id VARCHAR(36) COLLATE "C" PRIMARY KEY,
 mode VARCHAR(16) NOT NULL CHECK(mode IN ('DISTRIBUTABLE','TECHNICAL')),
 status VARCHAR(16) NOT NULL CHECK(status IN ('READY','BLOCKED','INVALID','TECHNICAL')),
 profile VARCHAR(16) NOT NULL CHECK(profile IN ('CORE','WITH_PHRASES')),
 snapshot_fingerprint VARCHAR(64) NOT NULL CHECK(snapshot_fingerprint ~ '^[a-f0-9]{64}$'),
 source_revisions TEXT NOT NULL,
 validation_results TEXT NOT NULL,
 release_id VARCHAR(71) COLLATE "C" REFERENCES publication_candidate(release_id),
 computed_release_id VARCHAR(71) COLLATE "C",
 created_at TIMESTAMPTZ NOT NULL,
 CHECK((status='READY' AND mode='DISTRIBUTABLE' AND release_id IS NOT NULL AND release_id=computed_release_id)
    OR (status<>'READY' AND release_id IS NULL)),
 CHECK((mode='TECHNICAL' AND status IN ('TECHNICAL','INVALID')) OR mode='DISTRIBUTABLE')
);
CREATE INDEX ix_publication_job_release ON publication_job(release_id);
