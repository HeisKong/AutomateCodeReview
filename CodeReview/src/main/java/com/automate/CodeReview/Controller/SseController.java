package com.automate.CodeReview.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/sse")
public class SseController {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam String repoId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(repoId, emitter);
        emitter.onCompletion(() -> emitters.remove(repoId));
        emitter.onTimeout(() -> emitters.remove(repoId));
        return emitter;
    }

    public void send(String key, Object data) {
        var emitter = emitters.get(key);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("message").data(data));
            } catch (IOException e) {
                log.warn("⚠️ SSE connection closed for key={} : {}", key, e.getMessage());
                emitters.remove(key);
            }
        } else {
            log.debug("No SSE emitter found for key={}", key);
        }
    }

}

