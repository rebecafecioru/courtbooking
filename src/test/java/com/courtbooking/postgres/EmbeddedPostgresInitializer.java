package com.courtbooking.postgres;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Points a Spring test context at a real Postgres server, started from
 * downloaded binaries (no Docker needed).
 * <p>
 * One server runs for the whole test run, but every context gets its own
 * new, empty database. Contexts that share a database can wipe each other's
 * data when they start (we hit that with H2), and each context here builds
 * its schema with Flyway from scratch, exactly like a fresh deployment.
 */
public class EmbeddedPostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static EmbeddedPostgres server;
    private static final AtomicInteger databaseCount = new AtomicInteger();

    private static synchronized EmbeddedPostgres server() {
        if (server == null) {
            try {
                server = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start embedded Postgres", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.close();
                } catch (IOException ignored) {
                    // the JVM is exiting anyway
                }
            }));
        }
        return server;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        EmbeddedPostgres postgres = server();
        String database = "courtbooking_test_" + databaseCount.incrementAndGet();
        try (Connection c = postgres.getPostgresDatabase().getConnection();
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + database);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create test database " + database, e);
        }

        TestPropertyValues.of(
                "spring.datasource.url=" + postgres.getJdbcUrl("postgres", database),
                "spring.datasource.username=postgres",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.postgresql.Driver"
        ).applyTo(context);
    }
}
