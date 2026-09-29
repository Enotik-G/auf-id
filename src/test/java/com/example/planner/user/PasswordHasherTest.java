package com.example.planner.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHasherTest {

    private static final String PEPPER = "test-pepper-only-for-tests-0123456789";

    private final PasswordHasher hasher = new PasswordHasher(PEPPER);

    @Test
    void hashIsArgon2idAndDoesNotContainPassword() {
        String hash = hasher.hash("correct horse battery staple");

        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hash).doesNotContain("correct horse");
    }

    @Test
    void acceptsCorrectPasswordAndRejectsWrongOne() {
        String hash = hasher.hash("correct horse battery staple");

        assertThat(hasher.matches("correct horse battery staple", hash)).isTrue();
        assertThat(hasher.matches("Correct horse battery staple", hash)).isFalse();
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfSalt() {
        assertThat(hasher.hash("qwerty123")).isNotEqualTo(hasher.hash("qwerty123"));
    }

    @Test
    void hashIsUselessWithoutThePepper() {
        String hash = hasher.hash("qwerty123");
        PasswordHasher otherPepper = new PasswordHasher("another-pepper-that-attacker-guessed-0000");

        assertThat(otherPepper.matches("qwerty123", hash)).isFalse();
    }

    @Test
    void refusesTooShortPepper() {
        assertThatThrownBy(() -> new PasswordHasher("short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
