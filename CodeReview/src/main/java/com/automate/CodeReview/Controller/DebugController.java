package com.automate.CodeReview.Controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DebugController {
    @GetMapping("/debug-auth")
    public Map<String, Object> debugAuth() {
        var ctx = SecurityContextHolder.getContext();
        var auth = ctx.getAuthentication();
        return Map.of(
                "authenticated", auth != null,
                "principal", auth != null ? auth.getPrincipal() : null,
                "authorities", auth != null ? auth.getAuthorities() : null
        );
    }
}

