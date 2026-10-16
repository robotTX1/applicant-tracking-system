package hu.robottx.ats.controller;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import hu.robottx.ats.exception.ConflictException;
import hu.robottx.ats.exception.ResourceLimitException;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.model.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class ControllerAdvice extends ResponseEntityExceptionHandler {

    private static final String CLIENT_ERROR_TYPE = "ClientError";
    private static final String SERVER_ERROR_TYPE = "ServerError";

    private static final String INTERNAL_SERVER_ERROR_TITLE = "Internal Server Error";
    private static final String INTERNAL_SERVER_ERROR_DETAILS = "Something went wrong";
    private static final String FORBIDDEN_TITLE = "Forbidden";
    private static final String FORBIDDEN_DETAILS = "You do not have the necessary permissions to access this resource";
    private static final String INVALID_REQUEST_TITLE = "Invalid request";
    private static final String FAILED_TO_VALIDATE_REQUEST = "Failed to validate request";
    private static final String RESOURCE_NOT_FOUND_TITLE = "Resource not found";
    private static final String BAD_REQUEST_TITLE = "Bad request";
    private static final String RESOURCE_LIMIT_REACHED_TITLE = "Resource limit reached";
    private static final String CONFLICT = "Conflict";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleException(IllegalArgumentException exception) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title(BAD_REQUEST_TITLE)
                .details(exception.getMessage())
                .status(HttpStatus.BAD_REQUEST)
                .build();
        return handleAllException(errorResponse);
    }

    @ExceptionHandler(ResourceLimitException.class)
    public ResponseEntity<Object> handleException(ResourceLimitException exception) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title(RESOURCE_LIMIT_REACHED_TITLE)
                .details(exception.getMessage())
                .status(HttpStatus.BAD_REQUEST)
                .build();
        return handleAllException(errorResponse);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleException(ConflictException exception) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title(CONFLICT)
                .details(exception.getMessage())
                .status(HttpStatus.CONFLICT)
                .build();
        return handleAllException(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleException(ResourceNotFoundException exception) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title(RESOURCE_NOT_FOUND_TITLE)
                .details(exception.getMessage())
                .status(HttpStatus.NOT_FOUND)
                .build();
        return handleAllException(errorResponse);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Object> handleException(AuthorizationDeniedException exception) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title(FORBIDDEN_TITLE)
                .details(FORBIDDEN_DETAILS)
                .status(HttpStatus.FORBIDDEN)
                .build();
        return handleAllException(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception exception) {
        log.error(exception.getMessage(), exception);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(SERVER_ERROR_TYPE)
                .title(INTERNAL_SERVER_ERROR_TITLE)
                .details(INTERNAL_SERVER_ERROR_DETAILS)
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build();
        return handleAllException(errorResponse);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(@NonNull HttpMediaTypeNotSupportedException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title("Unsupported Media Type")
                .details(ex.getMessage())
                .status(HttpStatus.BAD_REQUEST)
                .build();
        return handleAllException(errorResponse);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title(INVALID_REQUEST_TITLE)
                .details("Malformed request")
                .status(HttpStatus.BAD_REQUEST)
                .build();
        return handleAllException(errorResponse);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        String details = Optional.ofNullable(ex.getFieldError())
                .map(e -> "%s %s".formatted(e.getField(), e.getDefaultMessage()))
                .orElse(ex.getAllErrors().stream()
                        .findAny()
                        .map(ObjectError::getDefaultMessage)
                        .orElse(FAILED_TO_VALIDATE_REQUEST)
                );
        ErrorResponse errorResponse = ErrorResponse.builder()
                .type(CLIENT_ERROR_TYPE)
                .title(FAILED_TO_VALIDATE_REQUEST)
                .details(details)
                .status(HttpStatus.BAD_REQUEST)
                .build();
        return handleAllException(errorResponse);
    }

    private ResponseEntity<Object> handleAllException(ErrorResponse response) {
        return new ResponseEntity<>(response, response.getStatus());
    }

}
