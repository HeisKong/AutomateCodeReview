package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.NotiModel;
import com.automate.CodeReview.Service.NotiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/Notification")
public class NotiController {

    private final NotiService notiService;

    public NotiController(NotiService notiService) {
        this.notiService = notiService;
    }

    @PutMapping("/api/noti/{id}")
    public ResponseEntity<NotiModel> getReadCreateNoti(@PathVariable UUID id) {
        NotiModel updateCreateNoti = notiService.markAsRead(id);
        return ResponseEntity.ok(updateCreateNoti);
    }

}
