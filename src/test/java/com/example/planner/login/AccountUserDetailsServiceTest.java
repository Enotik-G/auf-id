package com.example.planner.login;

import com.example.planner.TestcontainersConfiguration;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredential;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, AccountUserDetailsService.class})
class AccountUserDetailsServiceTest {

    private static final String HASH = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";

    @Autowired
    private AccountUserDetailsService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Test
    void activeUserIsFoundByEmailAndNamedById() {
        User user = saveUser("ivan@mail.ru", true);

        UserDetails details = service.loadUserByUsername("  IVAN@Mail.ru ");

        assertThat(details.getUsername()).isEqualTo(user.getId().toString());
        assertThat(details.getPassword()).isEqualTo(HASH);
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void userWhoDidNotConfirmEmailIsDisabled() {
        saveUser("ivan@mail.ru", false);

        assertThat(service.loadUserByUsername("ivan@mail.ru").isEnabled()).isFalse();
    }

    @Test
    void unknownEmailIsNotFound() {
        assertThatThrownBy(() -> service.loadUserByUsername("nobody@mail.ru"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void malformedEmailIsNotFoundInsteadOfCrashing() {
        assertThatThrownBy(() -> service.loadUserByUsername("not-an-email"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private User saveUser(String email, boolean confirmed) {
        User user = User.selfRegistered(new EmailAddress(email), "Иван Петров");
        if (confirmed) {
            user.verifyEmail();
        }
        userRepository.save(user);
        credentialRepository.save(PasswordCredential.forUser(user, HASH));
        return user;
    }
}
