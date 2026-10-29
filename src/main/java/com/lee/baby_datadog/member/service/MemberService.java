package com.lee.baby_datadog.member.service;

import com.lee.baby_datadog.exception.ValidateMemberException;
import com.lee.baby_datadog.member.domain.Member;
import com.lee.baby_datadog.member.dto.CustomUserInfoDto;
import com.lee.baby_datadog.member.dto.LoginRequestDto;
import com.lee.baby_datadog.member.repository.MemberRepository;
import com.lee.baby_datadog.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;
    private final PasswordEncoder encoder;
    private final ModelMapper modelMapper;

    @Transactional
    public String login(LoginRequestDto dto){
        String email            = dto.getEmail();
        String password         = dto.getPassword();
        Member member = memberRepository.findMemberByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("이메일이 존재하지 않습니다."));

        // 암호화된 password를 디코딩한 값과 입력한 패스워드 값이 다르면 null 반환
        if(!encoder.matches(password, member.getPassword())){
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        CustomUserInfoDto info = modelMapper.map(member, CustomUserInfoDto.class);
        return jwtUtil.createAccessToken(info);
    }

    @Transactional
    public Long signup(Member member){
        Optional<Member> validMember = memberRepository.findMemberByEmail(member.getEmail());

        if(validMember.isPresent()) {
            throw new ValidateMemberException(member.getEmail() + " 은 이미 존재하는 이메일입니다. ");
        }

        // 비밀번호 해시 처리
        member.updatePassword(encoder.encode(member.getPassword()));
        memberRepository.create(member);
        return member.getId();
    }
}
