package com.automate.CodeReview.Models;

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
