package com.example.planner.login;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Страница входа. Саму форму обрабатывает Spring Security (POST /login),
 * а какие сообщения показать — шаблон решает по параметрам адреса: ?error, ?unconfirmed, ?logout.
 */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
