package com.automate.CodeReview.exception;

import com.automate.CodeReview.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** helper method ลด code ซ้ำ */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        req.getRequestURI()
                ));
    }

    /** ✅ ResponseStatusException (รวม custom เช่น UserNotFound, IssueNotFound) */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                              HttpServletRequest req) {
        HttpStatus status = (HttpStatus) ex.getStatusCode();
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, reason, req);
    }

    /** ✅ Validation จาก @Valid */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        String message = ex.getBindingResult().getAllErrors().isEmpty()
                ? "Validation failed"
                : ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    /** ✅ Validation จาก ConstraintViolation (เช่น @Size, @NotBlank) */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex,
                                                          HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    /** ✅ Bad Request เช่น UUID/JSON parse พัง */
    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex,
                                                          HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    /** ✅ Duplicate Key */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex,
                                                            HttpServletRequest req) {
        log.warn("DuplicateKeyException at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    /** ✅ Data Integrity Violation */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                      HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Duplicate data found in database", req);
    }

    /** ✅ WebClient ตอบกลับด้วย 4xx/5xx */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientResponse(WebClientResponseException ex,
                                                                 HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getRawStatusCode());
        String msg = "Upstream error: " + ex.getRawStatusCode() + " " + ex.getStatusText();
        return build(status != null ? status : HttpStatus.BAD_GATEWAY, msg, req);
    }

    /** ✅ WebClient ต่อไม่ได้ (network fail/DNS) */
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ErrorResponse> handleWebClientRequest(WebClientRequestException ex,
                                                                HttpServletRequest req) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Cannot reach upstream service", req);
    }

    /** ✅ Timeout (เช่น Reactor .timeout(...)) */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(TimeoutException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.GATEWAY_TIMEOUT, "Upstream timed out", req);
    }

    /** ✅ SonarApiException (custom exception ของเรา) */
    @ExceptionHandler(SonarApiException.class)
    public ResponseEntity<ErrorResponse> handleSonar(SonarApiException ex,
                                                     HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) status = HttpStatus.BAD_GATEWAY;

        log.warn("SonarApiException: status={}, path={}, bodyPreview={}",
                ex.getStatusCode(), req.getRequestURI(), ex.bodyPreview()); // ← ใช้ bodyPreview()

        return build(status, ex.getMessage(), req);
    }

    /** helper: ตัด string ไม่ให้ยาวเกิน log */
    private String preview(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    @ExceptionHandler(DuplicateFieldsException.class)
    public ResponseEntity<?> handleDuplicateFields(DuplicateFieldsException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Duplicate fields");
        body.put("fields", ex.getDuplicateFields());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }


    /** ✅ กันชนสุดท้าย */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                       HttpServletRequest req) {
        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req);
    }

}
