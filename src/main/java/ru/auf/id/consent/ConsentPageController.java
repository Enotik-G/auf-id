package ru.auf.id.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Текст согласия на обработку ПДн — на него ссылается страница активации. Открыт без входа. */
@Controller
@RequiredArgsConstructor
public class ConsentPageController {

    private final ConsentService consentService;

    @GetMapping("/consent/personal-data")
    public String personalData(Model model) {
        model.addAttribute("version", consentService.personalDataVersion());
        return "consent/personal-data";
    }
}
