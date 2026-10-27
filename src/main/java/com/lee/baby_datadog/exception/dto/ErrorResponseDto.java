package com.lee.baby_datadog.exception.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ErrorResponseDto {

    private final int status;
    private final String message;
    private LocalDateTime timestamp;

}
