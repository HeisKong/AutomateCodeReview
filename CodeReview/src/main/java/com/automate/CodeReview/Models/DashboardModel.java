package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardModel {

    private UUID id;
    private String name;
    private String metrics;
}
