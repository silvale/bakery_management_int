/*
 * Copyright© OneEmpower Pte Ltd. All rights reserved.
 *
 * This work contains trade secrets and confidential material of
 * OneEmpower Pte Ltd, and its unauthorised dissemination, use or
 * disclosure in whole or in part  without explicit written
 * permission of OneEmpower Pte Ltd is strictly prohibited.
 */
package com.bakery.framework.exception;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "Không hợp lệ",
                        (a, b) -> a));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Dữ liệu không hợp lệ");
        problem.setDetail("Dữ liệu không hợp lệ");
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Không tìm thấy");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title(status));
        problem.setDetail(ex.getReason());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Không có quyền");
        problem.setDetail("Không có quyền thực hiện thao tác này");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Yêu cầu không hợp lệ");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Không thực hiện được");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    /** 10/BE-2: UUID / boolean / ngày... sai kiểu ở query param hoặc path → 400 thay vì 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Yêu cầu không hợp lệ");
        problem.setDetail("Tham số " + ex.getName() + " không hợp lệ: " + ex.getValue());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    /** 10/BE-2: body thiếu / JSON hỏng / sai kiểu trường → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Yêu cầu không hợp lệ");
        problem.setDetail("Dữ liệu gửi lên không hợp lệ");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    /** 10/BE-2: trùng khóa / vi phạm ràng buộc DB → 409. Không trả câu SQL cho client, chỉ ghi log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc DB: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Xung đột dữ liệu");
        problem.setDetail("Dữ liệu trùng hoặc vi phạm ràng buộc");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * 11/E1: lỗi của Spring MVC (thiếu tham số/phần multipart, sai Content-Type, sai method, không có đường dẫn...) mang sẵn
     * status đúng (400/415/405/404); nếu không bắt thì rơi vào handler chung và thành 500.
     */
    @ExceptionHandler(jakarta.servlet.ServletException.class)
    public ResponseEntity<ProblemDetail> handleServlet(jakarta.servlet.ServletException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String detail = ex.getMessage();
        if (ex instanceof org.springframework.web.ErrorResponse er) {
            HttpStatus resolved = HttpStatus.resolve(er.getStatusCode().value());
            if (resolved != null) status = resolved;
            if (er.getBody().getDetail() != null) detail = er.getBody().getDetail();
        }
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title(status));
        problem.setDetail(detail);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).body(problem);
    }

    /** Upload sai kiểu (không phải multipart) → 400 thay vì 500. */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ProblemDetail> handleMultipart(org.springframework.web.multipart.MultipartException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Yêu cầu không hợp lệ");
        problem.setDetail("Yêu cầu tải lên phải là multipart/form-data");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Lỗi hệ thống");
        problem.setDetail("Lỗi hệ thống, vui lòng thử lại sau");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.internalServerError().body(problem);
    }

    /** 26: tiêu đề tiếng Việt theo mã HTTP (thay cho cụm tiếng Anh của Spring). */
    private static String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Yêu cầu không hợp lệ";
            case UNAUTHORIZED -> "Chưa đăng nhập";
            case FORBIDDEN -> "Không có quyền";
            case NOT_FOUND -> "Không tìm thấy";
            case METHOD_NOT_ALLOWED -> "Không hỗ trợ thao tác này";
            case CONFLICT -> "Xung đột dữ liệu";
            case UNSUPPORTED_MEDIA_TYPE -> "Kiểu dữ liệu không được hỗ trợ";
            case UNPROCESSABLE_ENTITY -> "Không thực hiện được";
            case INTERNAL_SERVER_ERROR -> "Lỗi hệ thống";
            default -> "Lỗi";
        };
    }
}
