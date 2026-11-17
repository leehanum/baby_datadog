# Spring Boot 4에서 Spring Security + JWT 적용하기

[Spring Security와 JWT 적용 과정](https://prao.tistory.com/entry/Spring-Security-Spring-Security%EC%99%80-JWT-%EC%A0%81%EC%9A%A9-%EA%B3%BC%EC%A0%95) 글을 따라 회원가입·로그인·JWT 인증을 구현했다. 코드를 따라 치는 것보다 **요청 하나가 Spring Security 안에서 어떤 순서로 처리되는지** 이해하는 데 초점을 두고 정리했다.

| 항목 | 버전 |
|---|---|
| Spring Boot | 4.1.1 (Spring Security 7) |
| Java | 21 |
| JWT 라이브러리 | jjwt 0.12.6 |
| 빌드 / DB | Maven / MySQL 8.4 |

## 1. 먼저 큰 그림: Spring Security는 필터 체인이다

Spring Security는 컨트롤러(DispatcherServlet) 앞에 놓인 **필터 묶음**이다. 모든 요청은 컨트롤러에 닿기 전에 이 필터들을 차례로 통과하고, 각 필터가 "이 요청은 누구인가(인증)", "이 요청을 허용할 것인가(인가)"를 나눠서 처리한다.

이번 구현에서 알아야 할 구성 요소는 이 정도다.

| 구성 요소 | 역할 | 이 프로젝트에서 |
|---|---|---|
| `SecurityFilterChain` | 어떤 필터를 쓰고 어떤 경로를 허용할지 설정 | `SecurityConfig` |
| `UserDetails` | Security가 이해하는 사용자 정보 (아이디, 비밀번호, 권한) | `CustomUserDetails` |
| `UserDetailsService` | 아이디로 사용자를 조회해 `UserDetails`로 반환 | `CustomUserDetailsService` |
| `Authentication` | 인증된 사용자 정보 + 권한을 담은 객체 | `UsernamePasswordAuthenticationToken` |
| `SecurityContextHolder` | 현재 요청의 `Authentication`을 보관하는 곳 | `JwtAuthFilter`가 채움 |
| `PasswordEncoder` | 비밀번호 해시·비교 | `BCryptPasswordEncoder` |
| `AuthenticationEntryPoint` / `AccessDeniedHandler` | 인증 실패(401) / 권한 부족(403) 응답 | `Custom...` 핸들러 |

핵심은 **`SecurityContextHolder`에 `Authentication`이 들어 있으면 "인증된 요청"으로 취급된다**는 점이다. 세션 방식에서는 세션이 이걸 채워 주지만, JWT 방식에서는 매 요청마다 토큰을 검증해서 직접 채워 넣어야 한다. 그 일을 하는 게 커스텀 필터 `JwtAuthFilter`다.

## 2. SecurityConfig: JWT에 맞게 기본 동작 끄기

Spring Security의 기본 설정은 세션 + 폼 로그인 기반이다. JWT를 쓰는 REST API에서는 이걸 끄고 필요한 것만 남긴다.

```java
http.csrf(AbstractHttpConfigurer::disable);
http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
http.formLogin(AbstractHttpConfigurer::disable);
http.httpBasic(AbstractHttpConfigurer::disable);

http.addFilterBefore(new JwtAuthFilter(customUserDetailsService, jwtUtil),
        UsernamePasswordAuthenticationFilter.class);

http.exceptionHandling(e -> e
        .authenticationEntryPoint(customAuthenticationEntryPoint)
        .accessDeniedHandler(accessDeniedHandler));

http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(AUTH_WHITELIST).permitAll()
        .anyRequest().permitAll());
```

- **STATELESS**: 서버가 세션을 만들지도, 쓰지도 않는다. 사용자 상태는 토큰에만 있다.
- **CSRF 비활성화**: CSRF 공격은 브라우저가 쿠키(세션)를 자동으로 실어 보내는 점을 노린다. 토큰을 `Authorization` 헤더에 직접 넣는 방식이라 꺼도 된다.
- **formLogin / httpBasic 비활성화**: 로그인은 `/member/login` API가 직접 처리하므로 Security 기본 로그인 페이지와 Basic 인증은 필요 없다.
- **`addFilterBefore`**: `JwtAuthFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 넣어, 인가 판단 전에 SecurityContext가 채워지도록 한다.
- **`exceptionHandling`**: 핸들러에 `@Component`만 붙여서는 Security가 쓰지 않는다. 여기서 등록해야 동작한다.
- **`anyRequest().permitAll()`**: URL 단위로는 모두 열어 두고, 실제 권한 검사는 컨트롤러 메서드의 `@PreAuthorize`로 한다. 그래서 `@EnableMethodSecurity`를 붙였다.

참고 글의 `@EnableGlobalMethodSecurity(prePostEnabled = true)`는 Security 7에서 deprecated다. `@EnableMethodSecurity`는 `prePostEnabled`가 기본으로 켜져 있다.

## 3. 회원가입: 비밀번호는 해시로, 권한은 서버가 정한다

회원가입 자체는 Security 필터와 거의 관계가 없다. 화이트리스트(`/member/signup`)라 인증 없이 들어와서 컨트롤러 → 서비스로 간다. Security와 관련된 포인트는 두 가지다.

**① 비밀번호는 `PasswordEncoder`로 해시해서 저장한다.**

```java
member.updatePassword(encoder.encode(member.getPassword()));
```

DB에는 `$2a$10$...` 형태의 BCrypt 해시가 저장된다. BCrypt는 되돌릴 수 없고, 같은 비밀번호도 매번 다른 salt가 붙어 해시값이 달라진다. 그래서 로그인할 때는 복호화가 아니라 `encoder.matches(입력값, 저장된 해시)`로 비교한다.

**② role은 요청에서 받지 않는다.**

요청 DTO에 `role` 필드가 있으면 클라이언트가 `"role": "ADMIN"`을 보내 관리자로 가입할 수 있다. 요청 DTO에서는 role을 빼고 엔티티로 바꿀 때 `RoleType.USER`로 고정했다.

## 4. 로그인: 검증 후 JWT 발급

```
POST /member/login (email, password)
 → MemberService.login
    1. 이메일로 회원 조회        (없으면 UsernameNotFoundException)
    2. encoder.matches로 비밀번호 비교 (틀리면 BadCredentialsException)
    3. JwtUtil.createAccessToken → 토큰 문자열 반환
```

여기서 알아 둘 점은, 이 로그인은 **Spring Security의 표준 인증 경로(`AuthenticationManager` → `AuthenticationProvider`)를 타지 않는다**는 것이다. 폼 로그인에서는 `UsernamePasswordAuthenticationFilter`가 아이디·비밀번호를 받아 `AuthenticationManager`에 넘기지만, 이번 구현은 formLogin을 끄고 서비스에서 직접 비교한 뒤 토큰을 만든다. 로그인 시점에 SecurityContext에 아무것도 저장하지 않고, 이후 요청부터 토큰으로 인증한다.

### JWT에 무엇을 담나

토큰은 `Header.Payload.Signature` 세 부분이고, Payload에 회원 정보(claim)를 담는다.

```java
Claims claims = Jwts.claims()
        .add("memberId", member.getMemberId())
        .add("email", member.getEmail())
        .add("name", member.getName())
        .add("role", member.getRole().name())
        .build();
```

Payload는 Base64로 인코딩만 되어 있어서 누구나 열어 볼 수 있다. **비밀번호 같은 민감 정보는 넣으면 안 된다.** 서명(Signature)은 내용을 숨기는 게 아니라, 서버의 secret key로 서명했기 때문에 **중간에 내용이 바뀌면 검증에서 실패하게 만드는** 장치다.

claim에서 숫자를 꺼낼 때는 `(Long) claims.get("memberId")` 대신 `claims.get("memberId", Long.class)`로 타입을 넘긴다. JSON에는 Long/Integer 구분이 없어서 작은 숫자는 `Integer`로 읽히기 때문이다.

### jjwt 0.11 → 0.12

참고 글은 jjwt 0.11 기준이라 일부 API가 0.12에서 사라지거나 deprecated됐다.

| 0.11 (참고 글) | 0.12 |
|---|---|
| `Jwts.parserBuilder().setSigningKey(key)` | `Jwts.parser().verifyWith(key)` (`parserBuilder`는 삭제) |
| `Jwts.claims()` → `Claims` | `Jwts.claims()` → `ClaimsBuilder` (`.add(...).build()`) |
| `setSubject` / `setIssuedAt` / `setExpiration` | `subject` / `issuedAt` / `expiration` |
| `signWith(key, SignatureAlgorithm.HS256)` | `signWith(key)` (키 길이로 알고리즘 결정) |
| `parseClaimsJws(token).getBody()` | `parseSignedClaims(token).getPayload()` |

또 jjwt는 `jjwt-api`, `jjwt-impl`, `jjwt-jackson` 세 개를 모두 넣어야 한다. 마지막 `jjwt-jackson`은 Header/Payload를 JSON으로 바꾸는 구현체라, 빠지면 컴파일은 되는데 토큰을 만드는 순간 런타임 에러가 난다.

## 5. 인증이 필요한 요청: JwtAuthFilter

로그인 이후 요청은 `Authorization: Bearer <토큰>` 헤더를 달고 온다. `JwtAuthFilter`가 하는 일은 결국 **"토큰이 유효하면 SecurityContext에 Authentication을 넣는다"** 하나다.

```
요청 (Authorization: Bearer xxx)
 → JwtAuthFilter
    1. 헤더에서 "Bearer " 뒤 토큰 추출
    2. jwtUtil.isValidToken  — 서명·만료 검증
    3. 토큰에서 memberId 추출
    4. CustomUserDetailsService.loadUserByUsername(memberId) — DB에서 회원 조회 → UserDetails
    5. UsernamePasswordAuthenticationToken(userDetails, null, 권한) 생성
    6. SecurityContextHolder.getContext().setAuthentication(...)
 → 다음 필터 → (인가 검사) → 컨트롤러
```

몇 가지 짚어 둘 부분이 있다.

- **`OncePerRequestFilter`**: 한 요청에서 필터가 두 번 실행되지 않도록 보장하는 Spring의 필터 베이스 클래스다.
- **토큰이 없거나 틀려도 예외를 던지지 않는다.** SecurityContext를 비워 둔 채 `filterChain.doFilter`로 넘기기만 한다. 막을지 말지는 뒤에 있는 인가 단계가 결정한다. 그래서 화이트리스트 경로는 토큰 없이도 그대로 통과한다.
- **`UsernamePasswordAuthenticationToken`의 두 번째 인자(credentials)는 `null`**: 이미 토큰으로 인증이 끝났으니 비밀번호를 들고 다닐 필요가 없다. 세 번째 인자로 권한을 넘기는 생성자를 쓰면 "인증 완료" 상태의 객체가 만들어진다.
- **`UserDetails`의 username은 이메일이 아니라 memberId다.** `loadUserByUsername`이라는 이름과 달리, 토큰에서 꺼낸 memberId로 조회하도록 구현했다. Security는 username이 "사용자를 식별하는 문자열"이기만 하면 된다.
- **권한에는 `ROLE_` 접두사를 붙인다.** `@PreAuthorize("hasRole('ADMIN')")`는 내부적으로 `ROLE_ADMIN` 권한을 찾기 때문에, `CustomUserDetails.getAuthorities()`에서 `"ROLE_" + role`로 만들어 준다.

## 6. 막힌 요청: 401과 403은 누가 가르나

인가 단계에서 막히면 응답이 두 가지로 나뉜다.

| | `AuthenticationEntryPoint` | `AccessDeniedHandler` |
|---|---|---|
| 상황 | 누구인지 모름 (토큰 없음·만료·위조) | 누구인지는 아는데 권한 부족 (USER가 ADMIN API 호출) |
| 응답 | 401 UNAUTHORIZED | 403 FORBIDDEN |

헷갈리는 부분은, 인가 검사(`AuthorizationFilter`나 `@PreAuthorize`)에서 막히면 **두 경우 모두 똑같이 `AccessDeniedException`이 던져진다**는 점이다. 이걸 잡아서 둘 중 어디로 보낼지는 그 앞에 있는 `ExceptionTranslationFilter`가 정한다.

```
JwtAuthFilter                    토큰이 유효하면 SecurityContext 채움
→ AnonymousAuthenticationFilter  비어 있으면 "익명 사용자"로 채움
→ ExceptionTranslationFilter     뒤에서 올라온 예외를 잡아 분기
→ AuthorizationFilter / @PreAuthorize   권한 검사, 실패하면 AccessDeniedException
```

`ExceptionTranslationFilter`는 현재 사용자가 익명이면 EntryPoint(401)로, 인증된 사용자면 AccessDeniedHandler(403)로 보낸다. 결국 **401과 403을 가르는 건 예외 종류가 아니라 SecurityContext에 인증된 사용자가 있느냐**이고, 그걸 채우는 게 `JwtAuthFilter`다.

| 요청 (`@PreAuthorize("hasRole('ADMIN')")` API) | SecurityContext | 결과 |
|---|---|---|
| 토큰 없음 / 만료 | 익명 사용자 | 401 |
| USER 토큰 | `ROLE_USER` | 403 |
| ADMIN 토큰 | `ROLE_ADMIN` | 통과 |

JWT REST API에는 로그인 페이지가 없으니, 두 핸들러 모두 리다이렉트 대신 JSON 에러 응답을 직접 써서 내려준다. 이때 클라이언트가 받는 HTTP 상태 코드는 응답 DTO 안의 숫자가 아니라 `response.setStatus()`로 정해진다. 참고 글의 `AccessDeniedHandler`는 설명은 403인데 `setStatus`에 `UNAUTHORIZED`가 들어가 있어서, 그대로 쓰면 권한 부족 사용자에게 401이 나간다. 두 곳 모두 `FORBIDDEN`으로 맞췄다.

Boot 4는 Jackson 3를 쓰기 때문에, 핸들러에서 쓰는 `ObjectMapper`는 `com.fasterxml.jackson.databind`가 아니라 `tools.jackson.databind` 패키지다.

## 정리

```
[회원가입] 화이트리스트 → 비밀번호 BCrypt 해시, role=USER 고정 → 저장
[로그인]   화이트리스트 → 이메일 조회 + matches 비교 → JWT 발급
[API 요청] JwtAuthFilter: 토큰 검증 → UserDetails 조회 → SecurityContext에 저장
           → 인가 검사 → 통과 / 익명이면 401 / 권한 부족이면 403
```

세션 방식에서 서버가 기억하던 "이 사용자는 로그인했다"는 상태를, JWT 방식에서는 토큰이 대신 들고 다니고 `JwtAuthFilter`가 매 요청마다 SecurityContext로 옮겨 놓는다. 이 한 줄이 이해되면 나머지 설정은 그걸 위한 준비 작업이다.
