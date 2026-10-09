package ru.auf.id.admin;

import ru.auf.id.user.UserRepository;
import ru.auf.id.SecurityConfiguration;
import ru.auf.id.provisioning.EmailAlreadyTakenException;
import ru.auf.id.provisioning.Invitation;
import ru.auf.id.provisioning.ProvisioningService;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserNotFoundException;
import ru.auf.id.user.WrongUserStatusException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfiguration.class)
class AdminUserControllerTest {

    /** Нужен фильтру перепроверки сессии, который ставит SecurityConfiguration. */
    @MockitoBean
    private UserRepository userRepository;

    private static final UUID USER_ID = UUID.fromString("0199bc42-8f31-7a1e-9c55-2b7d4e6a1f90");
    private static final String CREATE_BODY = """
            {"email":"ivan.petrov@sinhub.ru","fullName":"Иван Петров","roles":["STUDENT"]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvisioningService provisioning;

    @MockitoBean
    private AdminUserService adminUsers;

    // ─────────────────────────── доступ ───────────────────────────

    @Test
    void anonymousGetsUnauthorizedInsteadOfRedirectToLoginPage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + USER_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminUsers);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + USER_ID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUsers);
    }

    @Test
    @WithMockUser(roles = "CURATOR")
    void curatorIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/block").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUsers);
    }

    // ─────────────────────────── выдача учёток ───────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createsUserAndReturnsActivationLink() throws Exception {
        when(provisioning.invite(any(), any(), any())).thenReturn(new Invitation(USER_ID, "raw-token"));

        mockMvc.perform(post("/api/v1/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.activationLink").value("http://localhost:8080/activate?token=raw-token"));

        verify(provisioning).invite(new EmailAddress("ivan.petrov@sinhub.ru"), "Иван Петров", Set.of(Role.STUDENT));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createsUserWithoutRoles() throws Exception {
        when(provisioning.invite(any(), any(), any())).thenReturn(new Invitation(USER_ID, "raw-token"));

        mockMvc.perform(post("/api/v1/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ivan@sinhub.ru","fullName":"Иван Петров"}"""))
                .andExpect(status().isCreated());

        verify(provisioning).invite(new EmailAddress("ivan@sinhub.ru"), "Иван Петров", Set.of());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsTakenEmailAsConflict() throws Exception {
        when(provisioning.invite(any(), any(), any()))
                .thenThrow(new EmailAlreadyTakenException(new EmailAddress("ivan.petrov@sinhub.ru")));

        mockMvc.perform(post("/api/v1/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refusesMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"не-почта","fullName":"Иван Петров"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(provisioning);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reissuesActivationLink() throws Exception {
        when(provisioning.reissueInvitation(USER_ID)).thenReturn(new Invitation(USER_ID, "fresh-token"));

        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/activation-link").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activationLink").value("http://localhost:8080/activate?token=fresh-token"));
    }

    // ─────────────────────────── чтение, блокировка, роли ───────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void showsUser() throws Exception {
        User user = User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров");
        user.grantRole(Role.STUDENT);
        when(adminUsers.get(USER_ID)).thenReturn(user);

        mockMvc.perform(get("/api/v1/admin/users/" + USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ivan@sinhub.ru"))
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.lastLoginAt").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsUnknownUserAsNotFound() throws Exception {
        when(adminUsers.get(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

        mockMvc.perform(get("/api/v1/admin/users/" + USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blocksAndUnblocks() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/block").with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock").with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminUsers).block(USER_ID);
        verify(adminUsers).unblock(USER_ID);
    }

    // ─────────────────────────── защита от CSRF ───────────────────────────

    /**
     * Админка входит по cookie сессии, поэтому чужая страница могла бы отправить форму от имени
     * вошедшего админа. Без токена CSRF такой запрос отклоняется, до сервиса дело не доходит.
     *
     * <p>Путь с настоящим токеном (cookie → заголовок) — в {@code AdminApiCsrfTest}.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void requestWithoutCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUsers);
    }

    /** Действие не подходит к статусу учётки — это конфликт (409) с понятным сообщением. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsWrongStatusAsConflict() throws Exception {
        doThrow(new WrongUserStatusException("Снять блокировку можно только с BLOCKED, сейчас ACTIVE"))
                .when(adminUsers).unblock(USER_ID);

        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Снять блокировку можно только с BLOCKED, сейчас ACTIVE"));
    }

    /**
     * Внутренний сбой — не «конфликт»: раньше любой {@code IllegalStateException} (например,
     * «SHA-256 недоступен») превращался в 409 и выглядел как подсказка администратору.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void internalFailureIsNotReportedAsConflict() {
        doThrow(new IllegalStateException("SHA-256 недоступен в этой JVM")).when(adminUsers).unblock(USER_ID);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock").with(csrf())))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsLastAdminAsConflict() throws Exception {
        doThrow(new LastAdminException("Блокировка администратора")).when(adminUsers).block(USER_ID);

        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/block").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void grantsAndRevokesRole() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + USER_ID + "/roles/CURATOR").with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/users/" + USER_ID + "/roles/CURATOR").with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminUsers).grantRole(USER_ID, Role.CURATOR);
        verify(adminUsers).revokeRole(USER_ID, Role.CURATOR);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refusesUnknownRoleName() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + USER_ID + "/roles/TEACHER").with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminUsers);
    }
}
