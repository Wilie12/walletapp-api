package com.nn.walletapp_api.shared.api.advice;

import com.nn.walletapp_api.support.BaseControllerTest;
import com.nn.walletapp_api.support.SecurityTestUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestAdviceController.class)
@Import(GlobalExceptionHandlerTest.TestAdviceController.class)
public class GlobalExceptionHandlerTest extends BaseControllerTest {
    record SampleRequest(@NotBlank(message = "Title is required") String title) {}

    @RestController
    static class TestAdviceController {

        @PostMapping("/api/v1/test-validation")
        public String validate(@Valid @RequestBody SampleRequest request) {
            return "valid";
        }

        @GetMapping("/api/v1/test-forbidden")
        @PreAuthorize("hasRole('ADMIN')")
        public String securedAdmin() {
            return "admin-only";
        }

        @GetMapping("/api/v1/test-error")
        public String fail() {
            throw new RuntimeException("Simulated unexpected failure");
        }
    }

    @Test
    @DisplayName("Should return RFC 7807 400 Bad Request with invalidFields when validation fails")
    void shouldHandleValidationErrors() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String invalidPayload = "{\"title\": \"\"}";

        // when / then
        mockMvc.perform(post("/api/v1/test-validation")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/validation-failed"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"))
                .andExpect(jsonPath("$.invalidFields.title").value("Title is required"));
    }

    @Test
    @DisplayName("Should return RFC 7807 403 Forbidden when user lacks required role")
    void shouldHandleAccessDenied() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when / then
        mockMvc.perform(get("/api/v1/test-forbidden")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/access-denied"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }

    @Test
    @DisplayName("Should return RFC 7807 500 without stack trace leaks on unexpected exception")
    void shouldHandleGenericException() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when / then
        mockMvc.perform(get("/api/v1/test-error")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/internal-server-error"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
