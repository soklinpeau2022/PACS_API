package com.ut.emrPacs.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class FlywayMigrationConfigTest {

    @Test
    void shouldDisablePostgresqlTransactionalLockForConcurrentIndexMigrations() {
        Flyway flyway = new FlywayMigrationConfig().flyway(
                mock(DataSource.class),
                "classpath:db/migration",
                false,
                true,
                false,
                true,
                false,
                true,
                false,
                "CREATE EXTENSION IF NOT EXISTS pgcrypto; CREATE EXTENSION IF NOT EXISTS pg_trgm;"
        );

        PostgreSQLConfigurationExtension postgresql = flyway.getConfiguration()
                .getPluginRegister()
                .getPlugin(PostgreSQLConfigurationExtension.class);

        assertFalse(postgresql.isTransactionalLock());
    }
}
