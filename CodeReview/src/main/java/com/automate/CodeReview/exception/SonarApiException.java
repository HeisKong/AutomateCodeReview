package com.automate.CodeReview.exception;

public class SonarApiException extends RuntimeException {
    private static final int BODY_PREVIEW_MAX = 2_000; // กันล็อกยาวเป็นโลโก้บริษัท

    private final int statusCode;
    private final String responseBody;

    public SonarApiException(int statusCode, String message, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public SonarApiException(int statusCode, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /** สะดวกเวลาแปลงจาก WebClient */
    public static SonarApiException of(int statusCode, String path, String body) {
        String msg = "Sonar API failed: " + statusCode + " on " + path;
        return new SonarApiException(statusCode, msg, body);
    }

    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }

    /** helper ใช้ใน retry/filter */
    public boolean isServerError() { return statusCode >= 500; }

    /** ใช้ตอน log ให้เห็นบางส่วนพอเดาอาการ */
    public String bodyPreview() {
        if (responseBody == null) return null;
        return responseBody.length() <= BODY_PREVIEW_MAX
                ? responseBody
                : responseBody.substring(0, BODY_PREVIEW_MAX) + "...(truncated)";
    }

    @Override
    public String toString() {
        return "SonarApiException{statusCode=" + statusCode +
                ", message=" + getMessage() +
                ", bodyPreview=" + (responseBody == null ? "null" : bodyPreview()) +
                '}';
    }
}
