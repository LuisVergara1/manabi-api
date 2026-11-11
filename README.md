# Manabi API — administración de contenido educativo

Spring Boot, Java 21, JPA y PostgreSQL. El Quiz se ejecuta en Manabi Web sobre su catálogo local; no existen endpoints ni motor Quiz en este backend.

Módulos: Kana, Kanji, Vocabulario y Frases FIXED/PATTERN con categorías, ejemplos, significados, slots, claves y partículas. Los IDs públicos son independientes de las PK. El contrato público aprobado se mantiene en `../manabi-frontend/docs/contracts/manabi-json-contracts.md`. La API administrativa gestiona las revisiones y exporta publicaciones versionadas como ZIP.

## Configuración

Configura `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` en el entorno de ejecución. Usa `.env.example` como referencia y guarda los valores reales en configuración privada.

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/manabi'
$env:DB_USERNAME = 'manabi_runtime'
# Definir DB_PASSWORD de forma privada en el entorno de ejecución.
.\mvnw.cmd spring-boot:run
```

Flyway aplica las migraciones versionadas de `src/main/resources/db/migration/`. Hibernate valida el esquema y Flyway clean está deshabilitado.

Usa una instancia de PostgreSQL dedicada para el desarrollo y una instancia desechable para las pruebas de integración que escriben datos.

## Pruebas

```powershell
.\mvnw.cmd test
```

La suite normal no abre PostgreSQL. `EducationalDatabaseIT` queda fuera de la selección normal y requiere `MANABI_RUN_EDUCATIONAL_IT=true`, credenciales, destino verificado y el reset aprobado ya realizado:

```powershell
# Solo después de la autorización y reconstrucción.
$env:MANABI_RUN_EDUCATIONAL_IT = 'true'
.\mvnw.cmd '-Dtest=EducationalDatabaseIT' test
Remove-Item Env:MANABI_RUN_EDUCATIONAL_IT
```

Sus registros técnicos se revierten con transacciones de prueba; no constituyen contenido editorial.

Las pruebas optativas que verifican un destino concreto requieren MANABI_IT_EXPECTED_DB_URL, MANABI_IT_EXPECTED_SYSTEM_ID y, donde se verifica el usuario, MANABI_IT_EXPECTED_DB_USERNAME. Configúralos por separado en el entorno privado de pruebas.

## Contenido

Se conservan los JSON bajo `src/main/resources/educational-content/`: todavía son fuentes de publicación/pruebas del frontend. El importador histórico y sus tablas se retiraron; no se usa como sistema editorial. V1 solo incorpora las taxonomías aprobadas (20 categorías de vocabulario, incluidas dos ocultas; seis de frases). No precarga palabras, kanji, kana, frases o partículas inventados.

La administración utiliza Spring Security con sesión, CSRF y permisos por rol. Configura los orígenes autorizados y HTTPS en el entorno de despliegue.
