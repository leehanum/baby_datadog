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
