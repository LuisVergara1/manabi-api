-- Additive editorial metadata. No official content is loaded or approved here.
ALTER TABLE kanji_reading ADD COLUMN editorial_position INTEGER;
-- Reproduce the stage-2.1 comparator exactly, including Java/JS UTF-16 ordering.
WITH keyed AS (
 SELECT r.id,r.kanji_id,r.is_primary,r.reading_type,
  (SELECT COALESCE(string_agg(
    CASE WHEN ascii(ch)<65536 THEN lpad(to_hex(ascii(ch)),4,'0')
    ELSE lpad(to_hex(55296+((ascii(ch)-65536)/1024)),4,'0') ||
         lpad(to_hex(56320+((ascii(ch)-65536)%1024)),4,'0') END,
    '' ORDER BY ord),'')
   FROM regexp_split_to_table(r.reading,'') WITH ORDINALITY AS chars(ch,ord)) AS utf16_key
 FROM kanji_reading r
), ranked AS (
 SELECT id,(row_number() OVER(PARTITION BY kanji_id ORDER BY is_primary DESC,reading_type COLLATE "C",utf16_key COLLATE "C")-1)::INTEGER AS position
 FROM keyed
)
UPDATE kanji_reading r SET editorial_position=ranked.position FROM ranked WHERE ranked.id=r.id;
ALTER TABLE kanji_reading ALTER COLUMN editorial_position SET NOT NULL;
ALTER TABLE kanji_reading ADD CONSTRAINT ck_reading_editorial_position CHECK(editorial_position>=0);
ALTER TABLE kanji_reading ADD CONSTRAINT uq_reading_editorial_position UNIQUE(kanji_id,editorial_position) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE editorial_review (
 namespace VARCHAR(120) COLLATE "C" NOT NULL,
 entity_kind VARCHAR(32) COLLATE "C" NOT NULL CHECK(entity_kind IN ('KANA','KANJI','VOCABULARY','VOCABULARY_CATEGORY','PHRASE','PHRASE_CATEGORY','PARTICLE')),
 public_id VARCHAR(128) COLLATE "C" NOT NULL,
 state VARCHAR(12) NOT NULL CHECK(state IN ('PENDING','APPROVED')),
 revision_fingerprint VARCHAR(64) NOT NULL CHECK(revision_fingerprint ~ '^[a-f0-9]{64}$'),
 approved_by VARCHAR(160),
 approved_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(namespace,entity_kind,public_id),
 CHECK(btrim(namespace)<>'' AND btrim(public_id)<>''),
 CHECK((state='PENDING' AND approved_by IS NULL AND approved_at IS NULL) OR
       (state='APPROVED' AND approved_by IS NOT NULL AND btrim(approved_by)<>'' AND approved_at IS NOT NULL))
);
CREATE INDEX ix_editorial_review_state ON editorial_review(namespace,state,entity_kind,public_id);
CREATE TABLE editorial_bootstrap_load (
 id VARCHAR(64) PRIMARY KEY CHECK(id ~ '^[a-f0-9]{64}$'),
 namespace VARCHAR(120) COLLATE "C" NOT NULL,
 manifest_sha256 VARCHAR(64) NOT NULL CHECK(manifest_sha256 ~ '^[a-f0-9]{64}$'),
 approved_plan_sha256 VARCHAR(64) NOT NULL CHECK(approved_plan_sha256 ~ '^[a-f0-9]{64}$'),
 identities_sha256 VARCHAR(64) NOT NULL CHECK(identities_sha256 ~ '^[a-f0-9]{64}$'),
 source_hashes TEXT NOT NULL,
 applied_at TIMESTAMPTZ NOT NULL,
 UNIQUE(namespace,manifest_sha256)
);
