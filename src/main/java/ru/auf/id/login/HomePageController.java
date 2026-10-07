package ru.auf.id.login;

import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;

/** Страница «Вы вошли» — сюда попадают после входа, если не шли в какой-то конкретный сервис. */
@Controller
@RequiredArgsConstructor
public class HomePageController {

    private final UserRepository userRepository;

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        // Имя вошедшего — его id (см. AccountUserDetailsService).
        UUID userId = UUID.fromString(authentication.getName());
        User user = userRepository.findById(userId).orElseThrow();
        model.addAttribute("user", user);
        return "home";
    }
}
