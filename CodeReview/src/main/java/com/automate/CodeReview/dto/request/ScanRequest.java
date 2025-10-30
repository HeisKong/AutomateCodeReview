package com.automate.CodeReview.dto.request;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScanRequest {
    private String username;
    private String password;
}
