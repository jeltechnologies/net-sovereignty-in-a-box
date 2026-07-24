package com.jeltechnologies.portal.web;

import com.jeltechnologies.portal.security.PortalAuthFilter;
import com.jeltechnologies.portal.security.PortalCredentials;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

@Controller
public class LoginController {

    private final PortalCredentials credentials;

    public LoginController(PortalCredentials credentials) {
        this.credentials = credentials;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                         HttpServletResponse res, Model model) {
        if (!credentials.isValid(username, password)) {
            model.addAttribute("error", true);
            return "login";
        }

        ResponseCookie cookie = ResponseCookie.from(PortalAuthFilter.AUTH_COOKIE_NAME,
                        credentials.encodeBase64(username, password))
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return "redirect:/";
    }
}
