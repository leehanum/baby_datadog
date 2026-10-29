package com.lee.baby_datadog.member.controller;

import com.lee.baby_datadog.member.domain.Member;
import com.lee.baby_datadog.member.dto.LoginRequestDto;
import com.lee.baby_datadog.member.dto.MemberRequestDto;
import com.lee.baby_datadog.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/member")
public class MemberController {

    private final MemberService memberService;
    private final ModelMapper   modelMapper;

    @PostMapping("login")
    public ResponseEntity<String> getMemberProfile(
            @Valid @RequestBody LoginRequestDto request){

        String token = memberService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(token);
    }

    @PostMapping("siginup")
    public ResponseEntity<Long> signup(@Valid @RequestBody MemberRequestDto member){
        Member entity = modelMapper.map(member, Member.class);
        Long id = memberService.signup(entity);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }
}
