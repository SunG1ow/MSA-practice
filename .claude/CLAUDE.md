# MSA 실습 메모

## 작업 규칙
- 프로젝트 파일은 Claude가 절대 직접 수정하지 않는다. 설명, 리뷰, 빌드 확인만 한다.
- 코드는 사용자가 직접 작성하고, 수정 내용은 코드 예시로 안내한다.
- 검증이 필요하면 프로젝트 밖 임시 폴더의 복사본을 사용한다.

## 학습 정보
- 교재: 스프링 부트 3와 스프링 클라우드를 활용한 마이크로서비스 구축
- 저자 코드: https://github.com/wikibook/spring-microservice
- 교수님 진도에 맞춰 Boot 4.0.9 / Java 21 / Gradle 9.8.2 유지
  (저자 코드는 Boot 3.5.0 / Java 24 / Gradle 8.14)

## 현재 상태 (2026-11-02, 3장 89p)
- 구현 완료: api, util, product, recommendation, review, product-composite 서비스
- test-em-all.bash 전체 통과 (서비스 4개 실행 상태에서 확인)
- 포트: composite 7001, product 7002, recommendation 7003, review 7004

## 저자 코드와 다르게 한 부분
교재 코드를 옮길 때 아래 내용을 함께 바꾼다.

| 교재 코드 | 현재 프로젝트 |
|---|---|
| `UNPROCESSABLE_ENTITY` | `UNPROCESSABLE_CONTENT` |
| `server.error.include-message` | `spring.web.error.include-message` |
| `@SpringBootTest` 뒤 `WebTestClient` 사용 | `@AutoConfigureWebTestClient` 추가 |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| `catch (IOException ...)` (readValue) | `catch (JacksonException ...)` |
| `starter-test`, `reactor-test` | `starter-actuator-test`, `starter-webflux-test` |

- `AutoConfigureWebTestClient` import 경로:
  `org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient`
- `HttpStatus.resolve(422)`는 `UNPROCESSABLE_CONTENT`를 반환하므로 switch의 case도 변경해야 한다.
- 서비스별 `settings.gradle`은 삭제했다. 저자 폴더를 통째로 복사하면 다시 들어올 수 있고,
  있으면 VS Code에서 `Project with path ':api' could not be found` 오류가 난다.
- 서비스의 `build.gradle`에 `jar { enabled = false }`를 두어 plain.jar를 만들지 않는다.

## Jackson 3의 final 필드 대응 (적용 완료)
증상: composite 응답에서 productId가 0, name이 null
DTO의 final은 유지하고 아래처럼 해결했다.
1. composite application.yml:
   spring.jackson.mapper.allow-final-fields-as-mutators: true
2. ProductCompositeServiceApplication의 RestTemplate 빈:
   RestTemplate restTemplate(JsonMapper mapper) {
     return new RestTemplate(List.of(new JacksonJsonHttpMessageConverter(mapper)));
   }

## VS Code 문제 해결
- 터미널 빌드는 되는데 빨간 줄이 남으면 VS Code가 이전 프로젝트 정보를 쓰는 경우가 많다.
- build.gradle 수정 후: Java: Reload Projects 실행
- 그래도 남으면: Java: Clean Java Language Server Workspace 실행 후 Reload and Delete 선택

## 이후 챕터 주의점 (저자 코드 비교 기준, Boot 4 실행은 미확인)
- 6장: Product, Recommendation, Review에서 final이 제거되고 setter 추가
- 7장: RestTemplate 제거, WebClient로 전환. restTemplate() 코드도 함께 삭제
- final 관련 yml 설정은 유지: HttpErrorInfo가 23장까지 final
- 7장 Event도 final이고 fasterxml import 사용. 메시지 수신 서비스에서 확인 필요
- 교재 yml을 통째로 덮어쓸 때 위 설정이 사라지지 않게 주의
