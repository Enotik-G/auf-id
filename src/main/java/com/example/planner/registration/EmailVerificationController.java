package com.example.planner.registration;

import com.example.planner.onetimetoken.InvalidOneTimeTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/email-verifications")
@RequiredArgsConstructor
@Tag(name = "Регистрация", description = "Саморегистрация по почте")
public class EmailVerificationController {

    private final RegistrationService registrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Подтвердить почту",
            description = "Принимает токен из ссылки в письме. После успеха аккаунт активен, а ссылка больше не работает.")
    @ApiResponse(responseCode = "204", description = "Почта подтверждена, аккаунт активен")
    @ApiResponse(responseCode = "400", description = "Ссылка недействительна, уже использована или устарела",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public void verify(@Valid @RequestBody EmailVerificationRequest request) {
        registrationService.confirmEmail(request.token());
    }

    @ExceptionHandler(InvalidOneTimeTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidToken(InvalidOneTimeTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
