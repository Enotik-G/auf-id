package com.example.planner.admin;

import com.example.planner.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminClientController.class)
@Import(SecurityConfiguration.class)
class AdminClientControllerTest {

    private static final String PUBLIC_BODY = """
            {"clientId":"planner","name":"Планировщик","kind":"PUBLIC",
             "redirectUris":["https://planner.college.ru/callback"]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminClientService clients;

    @Test
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clients"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clients);
    }

    @Test
    @WithMockUser(roles = "CURATOR")
    void curatorIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clients"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clients);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registersPublicClient() throws Exception {
        when(clients.register(any())).thenReturn(new ClientCredentials("planner", null));

        mockMvc.perform(post("/api/v1/admin/clients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(PUBLIC_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value("planner"))
                .andExpect(jsonPath("$.secret").doesNotExist());

        verify(clients).register(new ClientSpec("planner", "Планировщик", ClientKind.PUBLIC,
                Set.of("https://planner.college.ru/callback"), Set.of(), Set.of()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsSecretForConfidentialClient() throws Exception {
        when(clients.register(any())).thenReturn(new ClientCredentials("board", "the-secret"));

        mockMvc.perform(post("/api/v1/admin/clients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"board","name":"Доска","kind":"CONFIDENTIAL",
                                 "redirectUris":["https://board.college.ru/callback"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value("the-secret"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsBadSpecAsBadRequest() throws Exception {
        when(clients.register(any()))
                .thenThrow(new InvalidClientSpecException("Нужен хотя бы один адрес возврата"));

        mockMvc.perform(post("/api/v1/admin/clients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(PUBLIC_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Нужен хотя бы один адрес возврата"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsDuplicateAsConflict() throws Exception {
        when(clients.register(any())).thenThrow(new ClientAlreadyExistsException("planner"));

        mockMvc.perform(post("/api/v1/admin/clients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(PUBLIC_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listsClients() throws Exception {
        when(clients.list()).thenReturn(List.of(new ClientSummary(
                "planner", "Планировщик", ClientKind.PUBLIC,
                Set.of("https://planner.college.ru/callback"),
                Set.of("openid"), Instant.parse("2026-10-07T10:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value("planner"))
                .andExpect(jsonPath("$[0].kind").value("PUBLIC"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsUnknownClientAsNotFound() throws Exception {
        when(clients.get("nobody")).thenThrow(new ClientNotFoundException("nobody"));

        mockMvc.perform(get("/api/v1/admin/clients/nobody"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rotatesSecret() throws Exception {
        when(clients.rotateSecret("board")).thenReturn(new ClientCredentials("board", "fresh"));

        mockMvc.perform(post("/api/v1/admin/clients/board/secret").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("fresh"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unregistersClient() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/clients/planner").with(csrf()))
                .andExpect(status().isNoContent());

        verify(clients).unregister("planner");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refusesRotationForPublicClient() throws Exception {
        doThrow(new InvalidClientSpecException("У публичного клиента planner секрета нет"))
                .when(clients).rotateSecret("planner");

        mockMvc.perform(post("/api/v1/admin/clients/planner/secret").with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
