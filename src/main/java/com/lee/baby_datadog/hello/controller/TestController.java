package com.lee.baby_datadog.hello.controller;

import com.lee.baby_datadog.security.details.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public String user(@AuthenticationPrincipal CustomUserDetails user){
        return "USER OK: " + user.getMember().getEmail();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin(){
        return "ADMIN OK";
    }
}
