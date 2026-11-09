# MSA 실습 메모

## 작업 규칙
- 프로젝트 파일은 Claude가 절대 직접 수정하지 않는다. 설명, 리뷰, 빌드 확인만 한다.
- 코드는 사용자가 직접 작성하고, 수정 내용은 코드 예시로 안내한다.
- 검증이 필요하면 프로젝트 밖 임시 폴더의 복사본을 사용한다.

## 학습 정보
- 교재: 스프링 부트 3와 스프링 클라우드를 활용한 마이크로서비스 구축
- 저자 코드
  - 번역서: https://github.com/wikibook/spring-microservice
  - 최신 원서(Packt 4판): https://github.com/PacktPublishing/Microservices-with-Spring-Boot-and-Spring-Cloud-Fourth-Edition
  - 두 저장소는 전 챕터 코드가 동일하고 Boot 버전만 다르다 (3.5.0 / 3.5.16).
    Packt가 3.5.16으로 올린 이유: Testcontainers 1.21.0이 새 Docker Desktop에서 동작하지 않음 (issue #5)
- 교수님 진도에 맞춰 Boot 4.0.9 / Java 21 / Gradle 9.8.2 유지
  (저자 코드는 Boot 3.5.x / Java 24 / Gradle 8.14)

## 현재 상태 (2026-11-09, 5장 137p 완료)
- 3장: api, util, product, recommendation, review, product-composite 서비스 구현
- 4장: Dockerfile 4개, docker-compose.yml, docker 프로파일, test-em-all.bash(start/stop) 완료
- 5장: springdoc-openapi 적용, Swagger UI 및 OpenAPI 문서 테스트 성공
- Chapter05 저자 코드와 비교 완료: 아래 표의 의도된 차이 외에는 동일
- 포트
  - 로컬(java -jar): composite 7001, product 7002, recommendation 7003, review 7004
  - docker 프로파일: 모든 서비스 8080, 외부 공개는 composite만 8080:8080

## 저자 코드와 다르게 한 부분
교재 코드를 옮길 때 아래 내용을 함께 바꾼다.

| 교재 코드 | 현재 프로젝트 |
|---|---|
| Boot `3.5.0` / Java `24` | Boot `4.0.9` / Java `21` |
| `eclipse-temurin:24_36-jre-noble` | `eclipse-temurin:21-jre-noble` |
| springdoc `2.8.6` | springdoc `3.0.3` (Boot 4용) |
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
- Dockerfile의 `-Djarmode=layertools`는 Boot 4에서 deprecated이지만 동작하므로 교재대로 유지한다.
- yml의 Swagger 설명 문구 "Unprocessable Entity"는 문서용 텍스트라 교재대로 둔다.

## Jackson 3의 final 필드 대응 (적용 완료)
증상: composite 응답에서 productId가 0, name이 null
DTO의 final은 유지하고 아래처럼 해결했다.
1. composite application.yml:
   spring.jackson.mapper.allow-final-fields-as-mutators: true
2. ProductCompositeServiceApplication의 RestTemplate 빈:
   RestTemplate restTemplate(JsonMapper mapper) {
     return new RestTemplate(List.of(new JacksonJsonHttpMessageConverter(mapper)));
   }

## Docker 실습 메모 (4장)
- 각 서비스 yml 끝에 docker 프로파일 구역이 있어야 한다. 없으면 컨테이너 안에서 7002 등으로 떠서
  `curl: (56) Recv failure: Connection reset by peer`가 난다.
  ```yaml
  ---
  spring.config.activate.on-profile: docker

  server.port: 8080
  ```
  composite는 여기에 app.*-service.host를 product / recommendation / review, port를 8080으로 추가
- yml이나 코드를 바꾸면 jar와 이미지를 모두 다시 빌드해야 반영된다.
  ```bash
  ./gradlew build
  docker compose build
  ./test-em-all.bash start stop
  ```
- `test-em-all.bash start`는 `docker compose up -d`만 하므로 이미지를 다시 빌드하지 않는다.
- 로컬(java -jar)로 테스트할 때: `PORT=7001 ./test-em-all.bash`
- 단독 컨테이너 실행 후에는 8080 충돌을 막기 위해 `docker stop`으로 정리한다.
- 빌드 명령 경로는 `:microservices:product-service:build` (s 주의)
- WSL에서 docker가 없으면 Docker Desktop의 WSL Integration에서 Ubuntu를 켠다.

## VS Code 문제 해결
- 터미널 빌드는 되는데 빨간 줄이 남으면 VS Code가 이전 프로젝트 정보를 쓰는 경우가 많다.
- build.gradle 수정 후: Java: Reload Projects 실행
- 그래도 남으면: Java: Clean Java Language Server Workspace 실행 후 Reload and Delete 선택

## 이후 챕터 주의점 (저자 코드 비교 기준, Boot 4 실행은 미확인)
- 6장: Product, Recommendation, Review에서 final이 제거되고 setter 추가
- 6장 Testcontainers: Boot 4.0.9는 Testcontainers 2.0.5를 관리한다 (issue #5 문제는 없을 가능성 높음).
  저자 코드의 1.x 모듈명(`org.testcontainers:junit-jupiter`, `mongodb`, `mysql`)은 2.x에서
  `testcontainers-junit-jupiter`, `testcontainers-mongodb`, `testcontainers-mysql`로 바뀌었고,
  `MongoDBContainer`, `MySQLContainer`의 import 패키지도 바뀌었을 수 있다. 6장 진입 시 복사본으로 확인
- 7장: RestTemplate 제거, WebClient로 전환. restTemplate() 코드도 함께 삭제
- final 관련 yml 설정은 유지: HttpErrorInfo가 23장까지 final
- 7장 Event도 final이고 fasterxml import 사용. 메시지 수신 서비스에서 확인 필요
- 교재 yml을 통째로 덮어쓸 때 위 설정(include-message, final 필드, docker 프로파일)이 사라지지 않게 주의
