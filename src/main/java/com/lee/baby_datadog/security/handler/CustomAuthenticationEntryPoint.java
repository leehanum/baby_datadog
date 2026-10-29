package com.lee.baby_datadog.security.handler;

import com.lee.baby_datadog.exception.dto.ErrorResponseDto;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Spring Security에서 인증되지 않은 사용자가 보호된 리소스에 접근할 때 호출되는 핸들러
 * commence 메서드: 클라이언트의 요청 및 응답, 인증 예외 객체를 받음
 *  1. 인증되지 않은 접근 시 로그 기록
 *  2. ErrorResponseDto 객체를 생성하여 HTTP 상태코드, 예외 메세지, 현재 시간을 포함
 *  3. 객체를 JSON 형식으로 직렬화하여 응답 본문에 작성하고 응답 상태 코드를 401 UNAUTHORIZED으로 설정
 */
@Slf4j(topic = "UNAUTHORIZED_EXCEPTION_HANDLER")
@AllArgsConstructor
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        log.error("Not Authenticated Request", authException);

        ErrorResponseDto errorResponseDto = new ErrorResponseDto(HttpStatus.UNAUTHORIZED.value(),
                authException.getMessage(),
                LocalDateTime.now());

        String responseBody = objectMapper.writeValueAsString(errorResponseDto);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(responseBody);

    }
}
