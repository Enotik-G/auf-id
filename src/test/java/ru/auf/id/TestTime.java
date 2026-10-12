package ru.auf.id;

import java.time.Instant;

/** Момент «сейчас» для тестовых данных — один на все тесты, чтобы не брать настоящие часы. */
public final class TestTime {

    public static final Instant NOW = Instant.parse("2026-10-10T09:00:00Z");

    private TestTime() {
    }
}
