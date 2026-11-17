# baby-datadog

개인 실습용 Spring Boot 프로젝트. Spring Boot 4.1.1(Spring Security 7), Java 21, Maven, MySQL 8.4(Docker `baby-mysql`), JPA, Lombok.

## 실습 방식

- 사용자가 코드를 **직접 타이핑**하며 실습한다. 요청하지 않으면 코드를 대신 작성·수정하지 말고, 단계 안내와 리뷰·질문 답변 위주로 돕는다.
- 참고 블로그(Spring Security + JWT): https://prao.tistory.com/entry/Spring-Security-Spring-Security%EC%99%80-JWT-%EC%A0%81%EC%9A%A9-%EA%B3%BC%EC%A0%95
  - 블로그는 구버전(Boot 2~3, jjwt 0.11, Gradle) 기준이므로 Boot 4 / Security 7 / jjwt 0.12 / Maven에 맞게 바꿔 안내한다.

## 블로그 정리 (`docs/spring-security.md`)

- 실습 내용을 블로그 글로 옮기기 위해 이 파일에 마크다운으로 이어서 쓴다. 따로 요청이 없어도 한 단계가 끝나고 쓸 만한 내용이 생기면 Claude가 판단해서 추가한다.
- **Spring Security 핵심 개념 위주**로 쓴다. 회원가입 → 로그인 → 인증된 요청 → 401/403 처럼 **요청 흐름 순서**를 따라, 각 단계에서 어떤 Security 구성 요소가 어떤 역할을 하는지와 왜 그렇게 설정했는지를 설명한다.
- 트러블슈팅은 개념 이해에 도움이 되는 것만 한두 문장으로 짧게 넣는다. `javap`로 jar 열어보기, DB 테이블 대소문자 같은 세부 디버깅 과정은 쓰지 않는다.
- 참고 블로그·구버전과 달라진 점(Security 7, jjwt 0.12, Jackson 3 등)은 표나 짧은 문단으로 요약한다.
- 코드는 개념을 보여주는 핵심 부분만 짧게 발췌하고, 흐름은 텍스트 다이어그램으로 보여준다. 뻔한 설명, 대화 과정, 전체 코드 나열은 쓰지 않는다.
- 실제로 실행·확인한 사실만 쓴다. 확인하지 않은 에러 메시지나 동작을 지어내지 않는다.
- 문체는 `~했다`체. 기존 글의 흐름과 형식을 이어간다.
