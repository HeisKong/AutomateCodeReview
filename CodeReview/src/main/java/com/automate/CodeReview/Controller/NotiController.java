package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.NotiModel;
import com.automate.CodeReview.Service.NotiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/Notification")
public class NotiController {

    private final NotiService notiService;

    public NotiController(NotiService notiService) {
        this.notiService = notiService;
    }

    @PutMapping("/read/{id}")
    public ResponseEntity<NotiModel> getReadCreateNoti(@PathVariable UUID id) {
        NotiModel updateCreateNoti = notiService.markAsRead(id);
        return ResponseEntity.ok(updateCreateNoti);
    }

    @GetMapping
    public List<NotiModel> getAllNoti(){
        return notiService.getAllNotification();
    }

    @DeleteMapping("delete/{id}")
    public ResponseEntity<NotiModel> deleteNoti(@PathVariable UUID id) {
        notiService.deleteNoti(id);
        return ResponseEntity.noContent().build();
    }

}
