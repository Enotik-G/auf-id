package ru.auf.id.provisioning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void longUncommonPasswordIsAccepted() {
        assertThat(policy.check("correct horse battery staple")).isEqualTo(PasswordPolicy.Verdict.OK);
    }

    @Test
    void twelveCharactersIsEnoughElevenIsNot() {
        assertThat(policy.check("abcdefghijk")).isEqualTo(PasswordPolicy.Verdict.WRONG_LENGTH);
        assertThat(policy.check("zq8vtm2kd0wp")).isEqualTo(PasswordPolicy.Verdict.OK);
    }

    @Test
    void tooLongIsRefused() {
        assertThat(policy.check("a".repeat(129))).isEqualTo(PasswordPolicy.Verdict.WRONG_LENGTH);
    }

    /** Длина считается в символах: эмодзи — один символ, хотя в Java это два char. */
    @Test
    void lengthIsCountedInCharactersNotChars() {
        assertThat(policy.check("😀".repeat(11))).isEqualTo(PasswordPolicy.Verdict.WRONG_LENGTH);
    }

    @Test
    void commonPasswordIsRefusedRegardlessOfCase() {
        assertThat(policy.check("qwertyuiop123")).isEqualTo(PasswordPolicy.Verdict.COMMON);
        assertThat(policy.check("QwertyUIOP123")).isEqualTo(PasswordPolicy.Verdict.COMMON);
    }
}
