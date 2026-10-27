package com.lee.baby_datadog.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberDto {

    private Long id;
    private String email;
    private String password;
    private String name;
}
