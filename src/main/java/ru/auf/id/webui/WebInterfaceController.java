package ru.auf.id.webui;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Страницы веб-интерфейса AUF ID — личный кабинет ({@code /account}) и панель администратора
 * ({@code /admin}). Сам интерфейс — одностраничное приложение на React (папка {@code frontend/}),
 * Maven собирает его в {@code static/}: {@code index.html} и файлы в {@code /assets/}.
 *
 * <p>Страница у такого приложения одна, а адресов много ({@code /admin/users/…}): какую часть
 * показать, решает React по адресу в браузере. Поэтому на любой из них сервер отдаёт тот же
 * {@code index.html} — внутренней переадресацией ({@code forward}): адрес в браузере не меняется.
 *
 * <p>Кому что можно — в {@code SecurityConfiguration}: кабинет — любому вошедшему, админка —
 * только {@code ADMIN}. Данные интерфейс всё равно берёт из API, где права проверяются ещё раз.
 */
@Controller
public class WebInterfaceController {

    /** Шаблоны адресов интерфейса. {@code /**} включает и сам корень: {@code /admin} тоже подходит. */
    public static final String ACCOUNT_PAGES = "/account/**";
    public static final String ADMIN_PAGES = "/admin/**";

    /**
     * CSP: браузер выполнит только скрипты с нашего адреса и будет обращаться только к нему же. Даже
     * если в интерфейс как-то попадёт чужой {@code <script>}, браузер его не запустит — а интерфейс
     * работает с сессией входа всей экосистемы.
     *
     * <p>Исключение одно — {@code style-src 'unsafe-inline'}: компоненты (Radix) вставляют небольшие
     * {@code <style>}, а встроенные стили, в отличие от скриптов, кода не выполняют.
     * {@code frame-ancestors 'none'} — интерфейс нельзя встроить в чужую страницу (кликджекинг).
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /**
     * Заголовок ставится здесь, до переадресации, а не правилом в {@code SecurityConfiguration}:
     * Spring Security пишет заголовки в момент отправки ответа и сверяет адрес тогда же — а к этому
     * моменту запрос уже переадресован на {@code /index.html} и под правило не попадает. При
     * переадресации тело ответа очищается, заголовки — нет.
     */
    @GetMapping({ACCOUNT_PAGES, ADMIN_PAGES})
    public String webInterface(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        return "forward:/index.html";
    }
}
