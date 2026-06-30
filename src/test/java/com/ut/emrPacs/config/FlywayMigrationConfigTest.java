package com.ut.emrPacs.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlywayMigrationConfigTest {

    @Test
    void shouldDisablePostgresqlTransactionalLockForConcurrentIndexMigrations() {
        Flyway flyway = createFlyway("CREATE EXTENSION IF NOT EXISTS pgcrypto; CREATE EXTENSION IF NOT EXISTS pg_trgm;");

        PostgreSQLConfigurationExtension postgresql = flyway.getConfiguration()
                .getPluginRegister()
                .getPlugin(PostgreSQLConfigurationExtension.class);

        assertFalse(postgresql.isTransactionalLock());
    }

    @Test
    void shouldRunExtensionSqlWithAfterConnectCallback() throws Exception {
        Flyway flyway = createFlyway("SELECT 1;");
        Callback callback = flyway.getConfiguration().getCallbacks()[0];
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        assertTrue(callback.supports(Event.AFTER_CONNECT, context));
        assertFalse(callback.supports(Event.BEFORE_MIGRATE, context));
        assertFalse(callback.canHandleInTransaction(Event.AFTER_CONNECT, context));
        assertEquals("after-connect-sql", callback.getCallbackName());
        assertNull(flyway.getConfiguration().getInitSql());

        callback.handle(Event.AFTER_CONNECT, context);

        verify(statement).execute("SELECT 1;");
        verify(statement).close();
    }

    private static Flyway createFlyway(String initSql) {
        return new FlywayMigrationConfig().flyway(
                mock(DataSource.class),
                "classpath:db/migration",
                false,
                true,
                true,
                false,
                true,
                false,
                initSql
        );
    }
}
