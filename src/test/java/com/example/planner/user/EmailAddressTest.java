package com.example.planner.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void trimsSpacesAndLowercases() {
        assertThat(new EmailAddress("  Ivan.Petrov@Mail.RU ").value())
                .isEqualTo("ivan.petrov@mail.ru");
    }

    @Test
    void differentlyTypedAddressesAreEqual() {
        assertThat(new EmailAddress("IVAN@mail.ru"))
                .isEqualTo(new EmailAddress("ivan@MAIL.ru "));
    }

    @Test
    void keepsPlusAlias() {
        assertThat(new EmailAddress("ivan+test@gmail.com").value())
                .isEqualTo("ivan+test@gmail.com");
    }

    @Test
    void stripsNonBreakingSpaceFromCopyPaste() {
        assertThat(new EmailAddress(" ivan@mail.ru ").value())
                .isEqualTo("ivan@mail.ru");
    }

    @Test
    void lowercasesDottedCapitalIWithoutLocaleSurprises() {
        assertThat(new EmailAddress("INFO@MAIL.RU").value())
                .isEqualTo("info@mail.ru");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ivan", "@mail.ru", "ivan@", "ivan@@mail.ru", "a@b@mail.ru", "iv an@mail.ru"})
    void rejectsMalformedAddresses(String raw) {
        assertThatThrownBy(() -> new EmailAddress(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new EmailAddress(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooLongAddress() {
        String tooLong = "a".repeat(250) + "@mail.ru";

        assertThatThrownBy(() -> new EmailAddress(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
