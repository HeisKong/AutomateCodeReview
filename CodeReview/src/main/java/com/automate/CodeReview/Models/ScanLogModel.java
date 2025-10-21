package com.automate.CodeReview.Models;

import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScanLogModel {
    private UUID scanId;
    private List<String> lines = new ArrayList<>();
}
