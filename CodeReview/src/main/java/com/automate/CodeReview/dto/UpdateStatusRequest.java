package com.automate.CodeReview.dto;

public class UpdateStatusRequest {
    private String status;
    private String annotation;

    public UpdateStatusRequest() {}
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAnnotation() { return annotation; }
    public void setAnnotation(String annotation) { this.annotation = annotation; }
}

