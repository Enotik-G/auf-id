package ru.auf.id.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Кого фильтр лимитов считает одним клиентом. */
class ClientKeyTest {

    @Test
    void ipv4IsTakenAsIs() {
        assertThat(RateLimitFilter.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void differentIpv4AddressesAreDifferentClients() {
        assertThat(RateLimitFilter.clientKey("203.0.113.7")).isNotEqualTo(RateLimitFilter.clientKey("203.0.113.8"));
    }

    @Test
    void addressesFromOneIpv6NetworkAreOneClient() {
        assertThat(RateLimitFilter.clientKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd"))
                .isEqualTo(RateLimitFilter.clientKey("2001:db8:1:2::1"))
                .isEqualTo("2001:db8:1:2:0:0:0:0/64");
    }

    @Test
    void differentIpv6NetworksAreDifferentClients() {
        assertThat(RateLimitFilter.clientKey("2001:db8:1:2::1"))
                .isNotEqualTo(RateLimitFilter.clientKey("2001:db8:1:3::1"));
    }
}
