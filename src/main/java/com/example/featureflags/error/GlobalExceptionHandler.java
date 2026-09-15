package com.example.featureflags.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every error is returned as RFC 9457 ProblemDetail JSON. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Race conditions: two creates with the same name, or concurrent updates. */
    @ExceptionHandler({DataIntegrityViolationException.class,
                       ObjectOptimisticLockingFailureException.class})
    public ProblemDetail concurrentModification(Exception e) {
        log.warn("Concurrent modification: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent modification, please retry");
    }

    /** @Valid body failures -> 400 with a field -> message map. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail malformedJson(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed or unreadable JSON body");
    }

    @ExceptionHandler({HandlerMethodValidationException.class,
                       ConstraintViolationException.class,
                       MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class})
    public ProblemDetail badRequest(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters");
    }

    /** Keep Spring's own statuses (404 no route, 405, 415); hide internals for everything else. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail fallback(Exception e) {
        if (e instanceof ErrorResponse er) {
            return er.getBody();
        }
        log.error("Unhandled error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
}
