package com.lee.baby_datadog.member.dto;

import com.lee.baby_datadog.member.domain.RoleType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CustomUserInfoDto extends MemberDto{

    private Long memberId;
    private String email;
    private String password;
    private String name;
    private RoleType role;
}
