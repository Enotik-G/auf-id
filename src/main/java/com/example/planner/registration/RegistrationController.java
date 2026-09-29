package com.example.planner.registration;

import com.example.planner.user.EmailAddress;
import com.example.planner.user.InvalidEmailException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * Саморегистрация. Всегда 202 — и для новой, и для занятой почты,
     * чтобы по ответу нельзя было узнать, зарегистрирован ли адрес.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegistrationRequest request) {
        EmailAddress email = new EmailAddress(request.email());
        try {
            registrationService.register(email, request.fullName().strip(), request.password());
        } catch (DataIntegrityViolationException sameEmailRegisteredConcurrently) {
            // Ответ тот же, что для занятой почты. См. javadoc RegistrationService.register.
        }
    }

    @ExceptionHandler(InvalidEmailException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidEmail(InvalidEmailException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
