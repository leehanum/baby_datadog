# Spring Boot 4에서 Spring Security + JWT 적용하기

[Spring Security와 JWT 적용 과정](https://prao.tistory.com/entry/Spring-Security-Spring-Security%EC%99%80-JWT-%EC%A0%81%EC%9A%A9-%EA%B3%BC%EC%A0%95) 글을 따라 실습하면서, 최신 버전 환경에 맞게 바꾼 부분을 정리했다.

## 환경

| 항목 | 버전 |
|---|---|
| Spring Boot | 4.1.1 (Spring Security 7) |
| Java | 21 |
| 빌드 | Maven |
| JWT 라이브러리 | jjwt 0.12.6 |
| DB | MySQL 8.4 (Docker) |

참고한 글은 Gradle + jjwt 0.11 기준이라 그대로 따라 하면 deprecated API나 컴파일 에러를 만나는 부분이 있다.

## 1. 의존성 추가

```xml
<dependency>
	<groupId>io.jsonwebtoken</groupId>
	<artifactId>jjwt-api</artifactId>
	<version>0.12.6</version>
</dependency>
<dependency>
	<groupId>io.jsonwebtoken</groupId>
	<artifactId>jjwt-impl</artifactId>
	<version>0.12.6</version>
	<scope>runtime</scope>
</dependency>
<dependency>
	<groupId>io.jsonwebtoken</groupId>
	<artifactId>jjwt-jackson</artifactId>
	<version>0.12.6</version>
	<scope>runtime</scope>
</dependency>
```

### jjwt는 왜 3개나 넣어야 할까

처음엔 `jjwt-api`, `jjwt-impl` 두 개면 될 거라고 생각했다. jjwt는 역할별로 jar가 나뉘어 있다.

| artifact | 역할 | 빠지면 |
|---|---|---|
| `jjwt-api` | 코드에서 쓰는 인터페이스 (`Jwts`, `Claims` 등) | 컴파일 에러 |
| `jjwt-impl` | 서명·파싱 실제 구현 | 런타임 에러 |
| `jjwt-jackson` | Header/Payload를 JSON으로 직렬화·역직렬화 | 토큰 생성·파싱 시점에 런타임 에러 |

JWT의 Header와 Payload는 JSON이라 직렬화 구현체가 반드시 필요하다. jjwt는 이 구현체를 classpath에서 찾는데, 프로젝트에 Jackson이 이미 있어도 **jjwt와 Jackson을 연결하는 `jjwt-jackson`이 없으면 찾지 못한다.** 컴파일은 통과하고 토큰을 처음 만드는 순간에야 터지기 때문에 놓치기 쉽다.

### `<scope>runtime</scope>`를 붙이는 이유

`jjwt-impl`, `jjwt-jackson`을 runtime scope로 두면 코드에서 `io.jsonwebtoken.impl...` 같은 내부 구현 클래스를 import할 수 없다. 공개 API(`jjwt-api`)만 쓰도록 강제하는 것으로, jjwt 공식 문서에서도 이렇게 권장한다.

## 2. JwtUtil: jjwt 0.11 → 0.12 마이그레이션

`JwtUtil`은 토큰 발급(`createAccessToken`), 검증(`isValidToken`), 내용 추출(`parseClaims`)을 담당한다. 참고 글의 코드를 그대로 옮기면 jjwt 0.12에서는 컴파일부터 안 된다.

```
incompatible types: io.jsonwebtoken.ClaimsBuilder cannot be converted to io.jsonwebtoken.Claims
cannot find symbol
  symbol:   method parserBuilder()
  location: class io.jsonwebtoken.Jwts
```

`Jwts` 클래스 자체는 그대로 있고 메서드가 바뀌었다. 0.12에서 builder/parser API가 정리되면서 `set` 접두사가 빠지고, 파싱 쪽 메서드 이름이 바뀌었다.

IDE에서 보면 빨간 줄(컴파일 에러)과 취소선(deprecated)이 섞여 있는데, 둘은 성격이 다르다. 0.12.6 jar를 `javap`로 직접 열어 확인했다.

- **삭제·변경되어 컴파일 에러:** `Jwts.parserBuilder()`는 메서드가 아예 없다. `Jwts.claims()`는 남아 있지만 반환 타입이 `Claims`에서 `ClaimsBuilder`로 바뀌었다.
- **deprecated라 아직 동작:** `setClaims`, `setIssuedAt`, `setExpiration`, `signWith(key, SignatureAlgorithm)`, `parseClaimsJws`. 지금은 컴파일되지만 1.0에서 제거될 예정이라 같이 바꿨다.

| 0.11 (참고 글) | 0.12 |
|---|---|
| `Key` | `SecretKey` |
| `Jwts.claims()` + `claims.put(...)` → `setClaims(claims)` | `Jwts.builder().claim(k, v)` |
| `setSubject` / `setIssuedAt` / `setExpiration` | `subject` / `issuedAt` / `expiration` |
| `signWith(key, SignatureAlgorithm.HS256)` | `signWith(key)` |
| `Jwts.parserBuilder().setSigningKey(key)` | `Jwts.parser().verifyWith(key)` |
| `parseClaimsJws(token).getBody()` | `parseSignedClaims(token).getPayload()` |

`signWith(key)`는 키 길이를 보고 알고리즘을 정한다. `openssl rand -base64 64`로 만든 64바이트 키를 넣으니 Header가 `{"alg":"HS512"}`로 나왔다.

```java
@Slf4j
@Component
public class JwtUtil {

	private final SecretKey key;
	private final long accessTokenExpTime;

	public JwtUtil(
			@Value("${jwt.secret}") String secretKey,
			@Value("${jwt.expiration_time}") long accessTokenExpTime) {
		this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
		this.accessTokenExpTime = accessTokenExpTime;
	}

	public String createAccessToken(CustomUserInfoDto member) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + accessTokenExpTime);

		return Jwts.builder()
				.subject(String.valueOf(member.getMemberId()))
				.claim("email", member.getEmail())
				.claim("name", member.getName())
				.claim("role", member.getRole().name())
				.issuedAt(now)
				.expiration(expiration)
				.signWith(key)
				.compact();
	}

	public Long getUserId(String token) {
		return Long.valueOf(parseClaims(token).getSubject());
	}

	public boolean isValidToken(String token) {
		try {
			Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
			return true;
		} catch (ExpiredJwtException e) {
			log.info("Expired JWT: {}", e.getMessage());
		} catch (JwtException | IllegalArgumentException e) {
			log.info("Invalid JWT: {}", e.getMessage());
		}
		return false;
	}

	public Claims parseClaims(String token) {
		try {
			return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		} catch (ExpiredJwtException e) {
			return e.getClaims();
		}
	}
}
```

회원 ID는 별도 claim 대신 JWT 표준 필드인 `sub`(subject)에 넣었다. subject는 문자열이라 꺼낼 때 `Long.valueOf`로 바꾼다.

### 함정 1: 만료 시간이 1000일이 된다

참고 글은 만료 시간을 이렇게 계산한다.

```java
ZonedDateTime tokenValidity = now.plusSeconds(expireTime);
```

그런데 설정값 `jwt.expiration_time=86400000`은 **밀리초**(24시간)다. 이걸 `plusSeconds`에 넣으면 86,400,000초, 즉 **1000일짜리 토큰**이 만들어진다. 컴파일도 실행도 정상이라 알아채기 어렵다. 위 코드처럼 밀리초 기준으로 계산하거나, 설정값 단위를 초로 바꿔야 한다.

### 함정 2: 서명이 틀린 토큰을 catch하지 못한다

참고 글의 검증 코드는 예외를 이렇게 잡는다.

```java
} catch (SecurityException | MalformedJwtException e) {
```

`import io.jsonwebtoken.*;`로 import하면 여기서 `SecurityException`은 jjwt의 `io.jsonwebtoken.security.SecurityException`이 아니라 **`java.lang.SecurityException`**으로 해석된다. 와일드카드 import는 하위 패키지(`io.jsonwebtoken.security`)를 포함하지 않기 때문이다.

다른 키로 서명한 토큰을 넣어 직접 확인해 보니, 이 catch 목록 중 어디에도 걸리지 않고 `io.jsonwebtoken.security.SignatureException`이 그대로 밖으로 던져졌다. `isValidToken`이 `false`를 반환하지 않고 예외를 던지면, 이걸 호출하는 JWT 필터에서 401이 아니라 500 에러가 난다.

jjwt의 예외는 모두 `JwtException`을 상속하므로, 위 코드처럼 만료(`ExpiredJwtException`)만 따로 잡고 나머지는 `JwtException`으로 한 번에 잡는 편이 안전하다.

### 함정 3: Long으로 넣은 claim이 Integer로 돌아온다

회원 ID를 subject가 아니라 `claim("memberId", 1L)`처럼 커스텀 claim으로 넣으면, 파싱해서 꺼낼 때 타입이 바뀐다. 직접 확인한 결과는 이렇다.

| 꺼내는 방법 | 결과 |
|---|---|
| `claims.get("memberId").getClass()` | `Integer` |
| `(Long) claims.get("memberId")` | `ClassCastException` |
| `claims.get("memberId", Long.class)` | `1` (정상) |

토큰 payload는 JSON이라 숫자의 원래 타입 정보가 없고, Jackson은 작은 숫자를 `Integer`로 읽는다. 커스텀 claim에서 숫자를 꺼낼 때는 캐스팅하지 말고 `get(name, Long.class)`처럼 타입을 넘겨야 한다.

## 3. 401과 403 나누기: AuthenticationEntryPoint vs AccessDeniedHandler

JWT 필터까지 만들고 나면, 막힌 요청에 어떤 응답을 줄지 정해야 한다. 참고 글은 핸들러를 두 개 만든다. 이름이 비슷해서 처음엔 헷갈렸는데, 기준은 하나다. **"누구인지 모르면" EntryPoint, "누구인지는 아는데 권한이 없으면" AccessDeniedHandler**다.

| | `CustomAuthenticationEntryPoint` | `CustomAccessDeniedHandler` |
|---|---|---|
| 상황 | 인증 실패 (토큰 없음, 만료, 위조) | 인증은 됐지만 권한 부족 (USER가 ADMIN API 호출) |
| 구현 인터페이스 | `AuthenticationEntryPoint` | `AccessDeniedHandler` |
| 메서드 | `commence(...)` | `handle(...)` |
| 응답 | 401 UNAUTHORIZED | 403 FORBIDDEN |

EntryPoint라는 이름은 원래 폼 로그인 시절, 미인증 사용자를 로그인 페이지로 보내 "인증을 시작하게 하는 입구"라는 뜻에서 왔다. JWT 기반 REST API에는 로그인 페이지가 없으니, 리다이렉트 대신 401 JSON을 내려주도록 직접 구현한다.

### 두 핸들러는 등록해야 동작한다

`@Component`를 붙이면 빈으로 등록되지만, Security가 그 빈을 알아서 가져다 쓰지는 않는다. `SecurityConfig`에서 예외 처리 담당으로 지정해야 한다.

```java
http.exceptionHandling(exceptionHandling -> exceptionHandling
		.authenticationEntryPoint(customAuthenticationEntryPoint)
		.accessDeniedHandler(accessDeniedHandler));
```

### 401과 403은 누가 어떻게 가르나

여기가 가장 헷갈렸던 부분이다. 직관적으로는 "인증 실패 예외"와 "권한 부족 예외"가 따로 터질 것 같지만, 인가 검사에서 막히면 **두 경우 모두 `AccessDeniedException`이 던져진다.** 이 예외를 잡아서 어느 핸들러로 보낼지 정하는 건 `ExceptionTranslationFilter`다.

Security 7.1.1의 기본 필터 순서를 확인해 보면 관련 필터는 이 순서로 놓인다. `JwtAuthFilter`는 `addFilterBefore`로 `UsernamePasswordAuthenticationFilter` 앞에 넣었다.

```
JwtAuthFilter                    토큰이 유효하면 SecurityContext에 인증 정보 저장
→ AnonymousAuthenticationFilter  SecurityContext가 비어 있으면 익명 사용자로 채움
→ ExceptionTranslationFilter     뒤에서 올라온 예외를 잡아 401/403 분기
→ AuthorizationFilter            URL 권한 검사, 실패하면 AccessDeniedException
→ 컨트롤러(@PreAuthorize)        메서드 권한 검사, 실패하면 AccessDeniedException
```

`ExceptionTranslationFilter`가 인가 검사보다 **앞**에 있어서, 뒤에서 터진 예외가 거슬러 올라오다 여기서 잡힌다. 7.1.1 jar의 `handleAccessDeniedException`을 `javap`로 열어보니 분기는 이랬다.

```java
// 단순화한 흐름
if (trustResolver.isAnonymous(authentication) || trustResolver.isRememberMe(authentication)) {
	// InsufficientAuthenticationException으로 감싸서
	sendStartAuthentication(...);        // → AuthenticationEntryPoint → 401
} else {
	accessDeniedHandler.handle(...);     // → AccessDeniedHandler → 403
}
```

결국 401과 403을 가르는 건 예외 종류가 아니라 **그 순간 SecurityContext에 인증된 사용자가 있느냐**다. 그리고 이걸 채우는 게 `JwtAuthFilter`다. 한 요청에서 두 핸들러가 함께 불리는 일은 없고, 인증 여부를 먼저 보기 때문에 미인증 사용자는 403까지 가지 않고 401로 끝난다.

| 요청 (`@PreAuthorize("hasRole('ADMIN')")` API) | SecurityContext | 처리 |
|---|---|---|
| 토큰 없음 / 만료 | 익명 사용자 | EntryPoint → 401 |
| USER 토큰 | `ROLE_USER` | AccessDeniedHandler → 403 |
| ADMIN 토큰 | `ROLE_ADMIN` | 통과 |

### 실수하기 쉬운 부분: 본문과 상태 코드는 따로 설정된다

`AccessDeniedHandler`를 작성하면서 처음에 이렇게 써 두었다.

```java
ErrorResponseDto errorResponseDto = new ErrorResponseDto(HttpStatus.FORBIDDEN.value(), ...);
response.setStatus(HttpStatus.UNAUTHORIZED.value());
```

`ErrorResponseDto`의 status는 JSON 본문에 들어가는 숫자일 뿐이고, 클라이언트가 실제로 받는 HTTP 상태 코드는 `response.setStatus()`로 정해진다. 이대로면 권한이 없는 사용자에게 상태 코드 401이 나간다. 프론트가 401을 보고 로그인 페이지로 보내는 식으로 분기하면, 이미 로그인한 사용자가 다시 로그인하라는 안내를 받게 된다. 두 곳 모두 `FORBIDDEN`으로 맞췄다.

`@Slf4j(topic = "FORBIDDEN_EXCEPTION_HANDLER")`의 topic은 로그에 찍히는 로거 이름일 뿐이라 상태 코드와는 관계가 없다.

### 참고 글과 달라진 점

- **Jackson 3:** Boot 4는 Jackson 3를 쓰기 때문에 핸들러에서 쓰는 `ObjectMapper`의 패키지가 `com.fasterxml.jackson.databind`가 아니라 `tools.jackson.databind`다.
- **`@EnableGlobalMethodSecurity`:** 7.1.1 jar에 아직 남아 있어서 컴파일은 되지만 deprecated다. 대체 어노테이션은 `@EnableMethodSecurity`이고, `prePostEnabled`가 기본값 true라 `@EnableMethodSecurity(securedEnabled = true)`면 된다.
