package com.automate.CodeReview.Models;

import lombok.*;

import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DashboardModel {

    private UUID id;
    private String name;
    private String metrics;
}
