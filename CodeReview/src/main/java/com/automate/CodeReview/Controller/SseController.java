package com.automate.CodeReview.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/sse")
@CrossOrigin(origins = "http://localhost:4200")
public class SseController {
    private final Map<String, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam String repoId) {
        SseEmitter emitter = new SseEmitter(0L);

        // ให้ repoId มีได้หลาย emitter
        emitterMap.computeIfAbsent(repoId, k -> new ArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> removeEmitter(repoId, emitter));
        emitter.onTimeout(() -> removeEmitter(repoId, emitter));

        return emitter;
    }


    public void send(String key, Object data) {
        List<SseEmitter> dead = new ArrayList<>();
        List<SseEmitter> emitters = emitterMap.getOrDefault(key, List.of());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("scan-complete")
                        .data(data));
            } catch (IOException e) {
                // ✅ สำคัญ: client หลุดแล้ว ให้จดไว้ลบ
                dead.add(emitter);
            }
        }

        // ลบตัวที่ตายออก
        emitters.removeAll(dead);
    }
    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> emitters = emitterMap.get(key);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emitterMap.remove(key);
            }
        }
    }

}

