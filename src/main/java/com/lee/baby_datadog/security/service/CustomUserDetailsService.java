package com.lee.baby_datadog.security.service;

import com.lee.baby_datadog.member.domain.Member;
import com.lee.baby_datadog.member.dto.CustomUserInfoDto;
import com.lee.baby_datadog.member.repository.MemberRepository;
import com.lee.baby_datadog.security.details.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String id) throws UsernameNotFoundException {
        Member member = memberRepository.findMemberById(Long.parseLong(id));

        if(member == null){
            throw new UsernameNotFoundException("해당하는 유저가 없습니다.");
        }

        CustomUserInfoDto dto = new CustomUserInfoDto(
                member.getId(),
                member.getEmail(),
                member.getPassword(),
                member.getName(),
                member.getRole()
        );

        return new CustomUserDetails(dto);
    }


}
