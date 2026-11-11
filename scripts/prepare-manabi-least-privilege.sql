-- PREPARATION ONLY. Never executed by the application or Flyway.
-- Run manually with an authorized database administrator, connected ONLY to manabi.
-- No passwords are stored here. NOLOGIN is intentional until credentials are configured separately.
BEGIN;
DO $$ BEGIN
 IF current_database() <> 'manabi' THEN RAISE EXCEPTION 'Only manabi is authorized'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='manabi_migrator') THEN
  CREATE ROLE manabi_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='manabi_runtime') THEN
  CREATE ROLE manabi_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname IN ('manabi_migrator','manabi_runtime') AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication)) THEN
  RAISE EXCEPTION 'Existing role privileges require explicit administrator review';
 END IF;
END $$;
GRANT CONNECT ON DATABASE manabi TO manabi_migrator, manabi_runtime;
GRANT USAGE,CREATE ON SCHEMA public TO manabi_migrator;
GRANT USAGE ON SCHEMA public TO manabi_runtime;
-- Existing installations: ownership transfer to the migrator must be reviewed separately.
-- GRANT alone does not make an existing table alterable by the migrator.
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO manabi_runtime;
REVOKE INSERT,UPDATE,DELETE ON TABLE public.flyway_schema_history FROM manabi_runtime;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO manabi_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE manabi_migrator IN SCHEMA public
 GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO manabi_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE manabi_migrator IN SCHEMA public
 GRANT USAGE,SELECT ON SEQUENCES TO manabi_runtime;
COMMIT;
-- Review these generated statements; this SELECT does not execute ownership changes.
SELECT format('ALTER TABLE %I.%I OWNER TO manabi_migrator;',schemaname,tablename)
 FROM pg_tables WHERE schemaname='public' ORDER BY tablename;
SELECT format('ALTER SEQUENCE %I.%I OWNER TO manabi_migrator;',schemaname,sequencename)
 FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename;
