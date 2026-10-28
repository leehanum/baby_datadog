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

/**
 * Spring Security에서 사용자 인증 정보를 로드하는 서비스
 * database에서 사용자 정보를 조회하여 'UserDatails' 객체로 변환
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * Member 객체를 CustomUserInfoDto 객체로 변환 후 CustomUserDetails 객체로 감싸서 반환
     */
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
