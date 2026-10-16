package ru.auf.id.login;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Главная страница AUF ID — сюда попадают после входа, если не шли в какой-то конкретный сервис.
 *
 * <p>Своей страницы у неё нет: человека сразу ведём в кабинет веб-интерфейса ({@code /account}).
 * Там то же, что было на прежней странице «Вы вошли» (имя, выход), и меню — профиль, пароль,
 * а администратору ещё и панель управления.
 */
@Controller
public class HomePageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/account";
    }
}
