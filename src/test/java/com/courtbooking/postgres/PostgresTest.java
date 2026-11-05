package com.courtbooking.postgres;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs a test against real Postgres with the production "postgres" profile
 * (Flyway migrations, schema validation), instead of the default in-memory H2.
 * <p>
 * Usually put on a subclass of an existing test, so the same test runs on
 * both databases: H2 is quick for everyday runs, but locking (SELECT ... FOR
 * UPDATE), deadlock detection and constraints are what Postgres does in
 * production, so that's where the concurrency claims have to hold.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        // The postgres profile reads these from the environment in production
        "app.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "app.cors.allowed-origins=http://localhost:5173"
})
@ActiveProfiles("postgres")
@ContextConfiguration(initializers = EmbeddedPostgresInitializer.class)
public @interface PostgresTest {
}
