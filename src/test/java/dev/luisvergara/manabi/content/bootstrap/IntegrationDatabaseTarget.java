package dev.luisvergara.manabi.content.bootstrap;

/** Explicit target allowlist for opt-in integration tests. */
public final class IntegrationDatabaseTarget {
    private IntegrationDatabaseTarget() {}

    public static String expected(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing integration target setting: " + variable);
        }
        return value;
    }
}
