package com.lee.baby_datadog.exception;

import com.lee.baby_datadog.exception.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidateMemberException.class)
    public ResponseEntity<ErrorResponseDto> handleValidateMember(ValidateMemberException e){
        ErrorResponseDto body = new ErrorResponseDto(HttpStatus.CONFLICT.value(),
                e.getMessage(),
                LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
