# MSA 실습 메모

## 작업 규칙
- 프로젝트 파일은 Claude가 절대 직접 수정하지 않는다. 설명, 리뷰, 빌드 확인만 한다.
- 코드는 사용자가 직접 작성하고, 수정 내용은 코드 예시로 안내한다.
- 안내할 때는 파일 경로, 줄 번호, 현재 코드 → 바꿀 코드를 명확히 적고, 헷갈리는 파일은 전체 내용을 준다.
  한 번에 한 서비스씩 진행하고 단계마다 빌드로 확인한다.
- 검증이 필요하면 프로젝트 밖 임시 폴더의 복사본을 사용한다.

## 학습 정보
- 교재: 스프링 부트 3와 스프링 클라우드를 활용한 마이크로서비스 구축
- 저자 코드
  - 번역서: https://github.com/wikibook/spring-microservice
  - 최신 원서(Packt 4판): https://github.com/PacktPublishing/Microservices-with-Spring-Boot-and-Spring-Cloud-Fourth-Edition
  - 두 저장소는 전 챕터 코드가 동일하고 Boot 버전만 다르다 (3.5.0 / 3.5.16).
    Packt가 3.5.16으로 올린 이유: Testcontainers 1.21.0이 새 Docker Desktop에서 동작하지 않음 (issue #5)
  - 파일 하나 받기: `curl -o 파일명 https://raw.githubusercontent.com/wikibook/spring-microservice/main/ChapterXX/파일명`
- 교수님 진도에 맞춰 Boot 4.0.9 / Java 21 / Gradle 9.8.2 / Spring Cloud 2025.1.4 유지
  (저자 코드는 Boot 3.5.x / Java 24 / Gradle 8.14 / Spring Cloud 2025.0.0)

## 현재 상태 (2026-11-24, 14장 완료)
- 3장: api, util, product, recommendation, review, product-composite 서비스 구현
- 4장: Dockerfile 4개, docker-compose.yml, docker 프로파일, test-em-all.bash(start/stop)
- 5장: springdoc-openapi 적용, Swagger UI 및 OpenAPI 문서 테스트
- 6장: 영속성 추가 (product·recommendation → MongoDB, review → MySQL)
- 7장: 리액티브 전환 + 이벤트 기반 비동기
  - 조회(GET): 논블로킹 동기 (WebClient + Mono.zip, 리액티브 Mongo, review는 JPA + jdbcScheduler)
  - 생성/삭제: composite가 StreamBridge로 이벤트 발행 → core 서비스의 MessageProcessorConfig가 소비 (202 Accepted)
  - ./gradlew build 통과, test-em-all 통과: 기본(RabbitMQ), partitions, kafka 3가지 모두
- 8장: 개념만 (저자 저장소에 Chapter08 없음)
- 9장: 서비스 디스커버리 (spring-cloud/eureka-server 추가)
  - 서비스 4개에 eureka-client, yml에 spring.application.name과 eureka 구역, 테스트는 `eureka.client.enabled=false`
  - composite: `@LoadBalanced WebClient.Builder` 빈, 호출 주소는 `http://product` 같은 서비스 이름
- 10장: 엣지 서버 (spring-cloud/gateway 추가)
  - 외부 공개는 gateway 하나뿐. composite와 eureka는 ports 삭제
  - 헬스 체크는 composite에서 gateway로 옮김 (composite의 HealthCheckConfiguration 삭제, Integration의 getXxxHealth 삭제)
  - composite yml: `springdoc.cache.disabled: true`, `server.forward-headers-strategy: framework`
  - 헤더 라우팅 대상: httpstat.us 대신 httpbin 컨테이너 (아래 표 참고)
- 11장: API 보안 (spring-cloud/authorization-server 추가, Spring Authorization Server)
  - Gateway HTTPS 8443 (자체 서명 keystore/edge.p12, 비밀번호 password), 토큰 유효 검사(401)
  - composite: scope 검사(GET은 product:read, POST/DELETE는 product:write, 없으면 403), Swagger OAuth(PKCE)
  - Eureka HTTP Basic 보호 (u/p)
  - 클라이언트 writer/secret-writer(read+write), reader/secret-reader(read), 사용자 u/p, 토큰 1시간, issuer http://auth-server:9999
  - test-em-all 통과 (기본 RabbitMQ 구성), 교재 OAuth 실습(client_credentials, 인가 코드+PKCE, Swagger) 완료
  - Auth0(외부 OIDC) 실습은 선택이라 진행하지 않음. 하게 되면 audience 파라미터, issuer-uri 끝 `/`,
    env.bash(이메일, 비밀번호, secret) 커밋 금지 주의
- 12장: 중앙 집중식 구성 (spring-cloud/config-server 추가, Spring Cloud Config)
  - config-server: 8888, `native` 프로파일, 저장소는 루트 `config-repo/` (로컬 `file:${PWD}/config-repo`, docker는 볼륨 `/config-repo`)
  - 서비스 7개 모두 `src/main/resources/application.yml` 삭제 → `bootstrap.yml`(이름, config uri, failFast, retry)만 남김.
    설정 내용은 `config-repo/application.yml`(공통) + `config-repo/{앱이름}.yml`(서비스별)
  - 앱 이름: product, recommendation, review, product-composite, gateway, eureka-server, auth-server(폴더명과 다름)
  - 구성 서버 보호 HTTP Basic dev-usr/dev-pwd, 암호화 키 my-very-secure-encrypt-key (루트 `.env`, compose가 자동으로 읽음)
  - config-repo의 비밀번호는 `{cipher}` 암호문. 구성 서버가 복호화해서 평문으로 내려준다
  - Gateway `/config/**` → config-server (RewritePath), SecurityConfig에서 permitAll (인증은 구성 서버의 Basic)
  - test-em-all 통과 (기본 RabbitMQ 구성), 교재 실습(설정 조회, encrypt/decrypt, `{cipher}` 복호화 확인) 완료
- 13장: 복원력 (Resilience4j, composite → product 호출에만 적용)
  - api `getProduct`에 `delay`, `faultPercent` 쿼리 파라미터 추가 (기본 0). product가 지연/에러를 일부러 낸다
  - composite Integration의 getProduct: `@Retry` `@TimeLimiter` `@CircuitBreaker(fallbackMethod = "getProductFallbackValue")`
    - 실행 순서: Retry(CircuitBreaker(TimeLimiter(호출)))
    - fallback은 회로가 OPEN일 때(`CallNotPermittedException`)만 실행. productId 13은 fallback 안에서 404, 나머지는 "Fallback product{id}"
  - 설정은 `config-repo/product-composite.yml`: TimeLimiter 2초, Retry 3회(1초 간격, InternalServerError만),
    CircuitBreaker COUNT_BASED 5회 중 50% 실패 → OPEN, 10초 뒤 자동 HALF_OPEN, 3회 성공 → CLOSED,
    InvalidInput·NotFound는 무시 (실패로 세지 않음)
  - composite 테스트: Mockito `getProduct(eq(ID), anyInt(), anyInt())` (매처는 인자 전부에 써야 함)
  - test-em-all 통과 (기본 RabbitMQ 구성), 교재 실습(OPEN/HALF_OPEN/CLOSED 전환, fallback, 재시도) 완료
- 14장: 분산 추적 (Micrometer Tracing + OpenTelemetry 브리지 + Zipkin)
  - 7개 모듈(config-server 제외): build.gradle 추적 의존성 4줄(spring-retry 아래),
    bootstrap.yml 2행에 `logging.pattern.correlation`(`[앱이름,traceId,spanId]`), `logging.include-application-name: false`
  - WebFlux 앱 5개 main 첫 줄 `Hooks.enableAutomaticContextPropagation()`
  - review: `datasource-micrometer-spring-boot:2.2.1` (JDBC span)
  - composite: `springBoot { buildInfo() }`, `starter-loadbalancer`, WebClient 빈(Boot Builder + `ReactorLoadBalancerExchangeFilterFunction`),
    Integration이 `WebClient`를 주입받음, `services/tracing/` 3개(ObservationUtil, ObservationRegistryConfig, BuildInfoObservationFilter),
    ServiceImpl 공개 메서드 3개를 `observationWithProductInfo`로 감쌈 (span "product info", 태그 productId, build.version)
  - config-repo: application.yml 공통 구역에 Zipkin 주소와 `sampling.probability: 1.0`, 서비스 4개 로그 레벨 DEBUG → INFO
  - compose 3개: `zipkin-server` (openzipkin/zipkin:3.5.0, STORAGE_TYPE=mem, mem_limit 1024m, 9411 공개)
  - 테스트 코드, test-em-all 변경 없음. test-em-all 통과 (기본 RabbitMQ 구성),
    교재 실습(GET/에러/비동기 POST trace, 로그 traceId grep, 태그 검색, 관계도) 완료
- 포트
  - 로컬(java -jar): composite 7001, product 7002, recommendation 7003, review 7004, eureka 8761, gateway 8443(https), auth-server 9999, config-server 8888
  - docker 프로파일: 모든 서비스 8080(auth-server 9999, config-server 8888), 외부 공개는 gateway 8443:8443과 zipkin 9411:9411
  - Gateway 경유 주소(https, -k): `/product-composite/**`(토큰 필요), `/openapi/swagger-ui.html`, `/eureka/web`·`/eureka/api/apps`(u/p), `/oauth2/**`·`/login`(auth-server)
  - DB/브로커: mongodb 27017, mysql 3306, rabbitmq 5672(관리 화면 15672, guest/guest), kafka 9092, zookeeper 2181
  - Zipkin: 9411 (`http://localhost:9411`, 서비스는 내부 이름 `zipkin-server:9411`로 전송)


## 저자 코드와 다르게 한 부분
교재 코드를 옮길 때 아래 내용을 함께 바꾼다.

| 교재 코드 | 현재 프로젝트 |
|---|---|
| Boot `3.5.x` / Java `24` | Boot `4.0.9` / Java `21` |
| `springCloudVersion = '2025.0.0'` | `'2025.1.4'` (Boot 4.0.9와 짝) |
| `eclipse-temurin:24_36-jre-noble` | `eclipse-temurin:21-jre-noble` |
| springdoc `2.8.6` | springdoc `3.0.3` (Boot 4용) |
| `UNPROCESSABLE_ENTITY` | `UNPROCESSABLE_CONTENT` |
| `server.error.include-message` | `spring.web.error.include-message` |
| `@SpringBootTest` 뒤 `WebTestClient` 사용 | `@AutoConfigureWebTestClient` 추가 |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| `catch (IOException ...)` (readValue) | `catch (JacksonException ...)` (`tools.jackson.core`) |
| `starter-test`, `reactor-test` | `starter-actuator-test`, `starter-webflux-test` |
| `spring.data.mongodb.host/port/database` | `spring.mongodb.host/port/database` |
| `org.testcontainers:junit-jupiter` / `mongodb` / `mysql` | `testcontainers-junit-jupiter` / `testcontainers-mongodb` / `testcontainers-mysql` |
| `org.testcontainers.containers.MongoDBContainer` | `org.testcontainers.mongodb.MongoDBContainer` |
| `org.testcontainers.containers.MySQLContainer` | `org.testcontainers.mysql.MySQLContainer` |
| `org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest` | `org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest` |
| `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` |
| (없음) | product·recommendation: `spring-boot-starter-data-mongodb-reactive-test` (6장은 `data-mongodb-test`) |
| (없음) | review: `spring-boot-starter-data-jpa-test` 추가 |
| `org.springframework.boot.actuate.health.*` | `org.springframework.boot.health.contributor.*` (Health, ReactiveHealthIndicator 등) |
| (없음) | composite: `spring-boot-starter-webclient` 추가 (WebClient.Builder 빈) |
| (없음) | 서비스 4개 모두 yml에 `spring.jackson.mapper.allow-final-fields-as-mutators: true` |
| `org.springframework.boot.test.web.client.TestRestTemplate` | `org.springframework.boot.resttestclient.TestRestTemplate` + 클래스에 `@AutoConfigureTestRestTemplate` |
| (없음) | eureka-server 테스트 의존성: `spring-boot-resttestclient`, `spring-boot-restclient` (RestTemplateBuilder) |
| health 기대값 `{"status":"UP"}` | `{"groups":["liveness","readiness"],"status":"UP"}` (eureka-server 테스트) |
| gateway 라우트 `uri: http://httpstat.us`, `SetPath=/200` | `uri: http://httpbin:8080`, `SetPath=/status/200` (418, 501도 같은 방식) |
| (없음) | compose 3개 끝에 `httpbin` 서비스 (`image: mccutchen/go-httpbin`, `mem_limit: 64m`, ports 없음) |
| `spring-boot-starter-oauth2-authorization-server` | `spring-boot-starter-security-oauth2-authorization-server` (옛 이름 deprecated) |
| `starter-security` + `spring-security-oauth2-resource-server` + `spring-security-oauth2-jose` | `spring-boot-starter-security-oauth2-resource-server` 1줄 (gateway, composite) |
| auth-server `testImplementation 'spring-boot-starter-test'` | `spring-boot-starter-webmvc-test` + `junit-platform-launcher` |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| `org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.*` / `...configuration.*` | `org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.*` / `org.springframework.security.config.annotation.web.configuration.*` |
| (없음) | composite yml `springdoc.swagger-ui.oauth.usePkceWithAuthorizationCodeGrant: true` (PKCE) |
| (없음) | authorization-server build.gradle에 `jar { enabled = false }` (없으면 Dockerfile ADD 실패) |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` (Boot 4 이름 변경) |
| `spring-cloud-config-client` + `starter-config` | `starter-config` 하나 (client 포함) |
| 테스트 yml `server.error.include-message` | `spring.web.error.include-message` (저자 12장 테스트 yml에도 옛 이름) |
| (없음) | composite 테스트 yml 1행 `spring.jackson.mapper.allow-final-fields-as-mutators: true` |
| (없음) | review 테스트 2개에 `spring.jpa.hibernate.ddl-auto=update` |
| auth-server 테스트 yml (저자 12장) | 만들지 않음 (`ml.` 로 시작하는 잘못된 줄이 섞여 있음, 없어도 통과) |
| `resilience4j-spring-boot3` `2.3.0` | `resilience4j-spring-boot4` `2.4.0` (bom도 2.4.0) |
| composite에 `spring-boot-starter-aop` 추가 | 추가하지 않음 (12장의 `starter-aspectj`가 이미 있음) |
| test-em-all 기대값 `CLOSED_TO_OPEN` 등 3개 | `"State transition from CLOSED to OPEN"` 등 (220~222행) |
| (없음) | composite 테스트 Mockito: `getProduct(eq(PRODUCT_ID_OK), anyInt(), anyInt())` |
| `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` | 위 2줄 + `spring-boot-micrometer-tracing-opentelemetry` + `spring-boot-zipkin` (7개 모듈, config-server 제외) |
| `management.zipkin.tracing.endpoint` | `management.tracing.export.zipkin.endpoint` (옛 이름은 level: error로 무시) |
| `org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer` | `org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryCustomizer` |
| review `datasource-micrometer-spring-boot:1.0.0` | `2.2.1` (1.x는 Boot 3, 2.0~2.2는 Boot 4.0, 2.3은 Boot 4.1) |
| composite `test { testLogging.showStandardStreams = true }` | 넣지 않음 (출력만 길어짐) |

- `AutoConfigureWebTestClient` import 경로:
  `org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient`
  testImplementation 의존성이라 `src/test`에서만 보인다. `src/main`의 Application 클래스에 넣으면 컴파일 에러.
- `HttpStatus.resolve(422)`는 `UNPROCESSABLE_CONTENT`를 반환하므로 switch의 case도 변경해야 한다.
- `spring.data.mongodb.*` 옛 이름은 Boot 4에서 무시된다(level: error). 테스트는 `@ServiceConnection` 덕분에
  통과해서 문제가 숨겨지고, Docker 실행 시 localhost로 접속해 타임아웃이 난다. yml의 기본 구역과
  docker 구역, Application 클래스의 `getProperty(...)` 모두 바꾼다.
- 그대로인 것: Java import `org.springframework.data.mongodb...`, `spring.datasource.*`, `spring.jpa.*`,
  `spring-cloud-stream-test-binder`, `management.health.rabbit.enabled`, `CompositeReactiveHealthContributor.fromMap`
- 서비스별 `settings.gradle`은 삭제했다. 저자 폴더를 통째로 복사하면 다시 들어올 수 있고,
  있으면 VS Code에서 `Project with path ':api' could not be found` 오류가 난다.
- 서비스의 `build.gradle`에 `jar { enabled = false }`를 두어 plain.jar를 만들지 않는다.
- `util/build.gradle`에 `implementation project(':util')`이 들어가면 순환 의존성으로 빌드가 멈춘다.
- MapStruct 버전은 Boot가 관리하지 않으므로 `ext { mapstructVersion = '1.6.3' }`로 직접 지정 (교재 그대로)
- Dockerfile의 `-Djarmode=layertools`는 Boot 4에서 deprecated이지만 동작하므로 교재대로 유지한다.
- yml의 Swagger 설명 문구 "Unprocessable Entity"는 문서용 텍스트라 교재대로 둔다.
- `Event`의 `@JsonSerialize`(fasterxml)는 교재대로 둔다. Spring Cloud Stream이 Jackson 2를 함께 가져와서
  컴파일은 되고, Jackson 3는 이 어노테이션을 무시한다. eventCreatedAt은 ISO 문자열(UTC, `Z`)로 나온다.
- `@AutoConfigureTestRestTemplate` import: `org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate`
- gateway의 HealthCheckConfiguration도 `org.springframework.boot.health.contributor.*` import로 바꾼다.
- `spring.cloud.gateway.server.webflux.*` 속성 이름은 2025.1.4에서 그대로 동작한다.
- httpstat.us는 응답하지 않아서 교수님 안내대로 httpbin으로 바꿨다. go-httpbin은 8080 포트를 쓴다(kennethreitz/httpbin은 80).
  compose 내부 이름으로 접근하므로 헤더 라우팅은 Docker 실행에서만 동작한다.
- Spring Authorization Server는 Spring Security 7에 합쳐졌다. 설정 클래스 위치:
  `org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer`,
  `org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration`
  (`...oauth2.server.authorization.client.*`, `settings.*`, `authentication.*` 등 나머지 import는 그대로)
- Boot 4 인가 서버는 Authorization Code 흐름에 PKCE를 기본으로 요구한다(Boot 3은 요구하지 않음).
  없으면 `invalid_request ... code_challenge`. Swagger UI는 composite yml
  `springdoc.swagger-ui.oauth.usePkceWithAuthorizationCodeGrant: true`로 해결.
  교재의 브라우저+curl 실습은 code_challenge / code_verifier를 직접 붙인다(아래 Docker 실습 메모).
- auth-server는 서블릿(Spring MVC) 앱이라 테스트가 MockMvc다. gateway·composite는 WebFlux.
- composite 테스트는 `classes = {TestSecurityConfig.class}`, `issuer-uri=`(빈 값), `allow-bean-definition-overriding=true`로
  보안을 끄고 비즈니스 로직만 검증한다. 보안 동작(401/403)은 test-em-all이 Docker에서 검증한다.
- Eureka 보호(u/p, HTTP Basic): eureka-server의 SecurityConfig, 등록하는 모든 서비스의 defaultZone에 `u:p@`,
  eureka-server 테스트는 `testRestTemplate.withBasicAuth(username, password)`
- 12장 서비스별 의존성 4줄 (eureka-client 아래, eureka-server는 `dependencies {` 바로 아래):
  `spring-cloud-starter-config`, `spring-cloud-starter-bootstrap`, `spring-boot-starter-aspectj`, `spring-retry`.
  aspectj나 retry가 빠지면 retry 설정이 무시되어 구성 서버가 늦게 뜰 때 바로 실패한다.
- 12장 테스트는 구성 서버 없이 돌린다: 모든 테스트 어노테이션에 `spring.cloud.config.enabled=false`
  (`@DataMongoTest(properties = ...)`, `@DataJpaTest(properties = ...)` 포함).
  main의 application.yml이 없어졌으므로 테스트에 필요한 값은 `src/test/resources/application.yml`에 둔다.
  - product·recommendation: `spring.web.error.include-message`, `spring.cloud.stream.defaultBinder: rabbit`
  - review: 위 + logging. 테이블 생성은 테스트 어노테이션의 `ddl-auto=update` (MySQL 컨테이너의 기본값은 none)
  - composite: allow-final + 빈 `api.common.*` + stream bindings(`products-out-0.destination` 등)
  - gateway: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: .` (JwtDecoder 빈만 생기게 하는 가짜 값)
  - eureka-server: `app.eureka-username: u`, `app.eureka-password: p` (테스트의 `@Value`)
- `include-message`가 빠지면 `getProductInvalidParameterString`만 실패한다(`No value at JSON path "$.message"`).
  타입 불일치는 Spring 기본 에러 처리라 이 설정이 필요하고, 음수 검사는 우리 핸들러가 message를 직접 넣어서 통과한다.
- gateway의 `keystore/edge.p12`는 src/main/resources에 남긴다. 구성 서버는 경로(`classpath:keystore/edge.p12`)만 내려준다.
- Jackson 3는 enum을 `toString()`으로 직렬화한다. Resilience4j의 상태 전환 enum은 toString이
  "State transition from CLOSED to OPEN"이라 `/actuator/circuitbreakerevents`의 stateTransition도 이 문자열로 나온다.
  `spring.jackson.datatype.enum.*`으로는 바뀌지 않는다. actuator는 별도 매퍼를 쓰기 때문이다
  (`management.endpoints.jackson.isolated-json-mapper` 기본값 true). 그래서 test-em-all 기대값을 바꿨다.
- `@Retry`, `@CircuitBreaker`, `@TimeLimiter` import는 `io.github.resilience4j...annotation.*`.
  spring-retry에도 이름이 같은 `@Retry`/`@CircuitBreaker`가 있으니 VS Code 자동 import에 주의.
- Boot 4는 추적 자동 구성이 모듈로 분리되어 있다. bridge-otel과 exporter-zipkin만 넣으면 조립이 안 되므로
  `spring-boot-micrometer-tracing-opentelemetry`(OTel 연결)와 `spring-boot-zipkin`(전송, `management.tracing.export.zipkin.*`)을 함께 넣는다.
  (`spring-boot-starter-zipkin`은 Brave 구현이라 교재의 OTel과 다름)
- 옛 속성 `management.zipkin.tracing.endpoint`는 무시되어 기본값 localhost:9411로 보낸다. 컨테이너 안에서
  `Failed to export spans` + `ConnectException`이 계속 나면 속성 이름이나 config-repo 구역 위치를 확인한다
- gateway의 `@LoadBalanced WebClient.Builder`는 그대로 둔다. 헬스 체크용이고, 라우팅은 Gateway 자체 클라이언트라 추적 헤더가 이미 붙는다.
  composite는 Boot의 Builder에 추적 설정이 있어서 `builder.filter(lbFunction).build()` WebClient 빈으로 바꿨다
- `Hooks.enableAutomaticContextPropagation()`은 WebFlux 앱 5개(product, recommendation, review, gateway, composite)의 main 첫 줄.
  eureka-server·auth-server는 서블릿이라 없음. review는 jdbcScheduler로 스레드가 바뀌어도 JDBC span이 같은 trace에 붙는 것을 확인
- composite `springBoot { buildInfo() }`가 없으면 `BuildProperties` 빈이 없어 시작 실패 (ObservationRegistryConfig가 사용)

## Jackson 3의 final 필드 대응 (적용 완료)
증상: final 필드 객체를 JSON에서 읽을 때 값이 비어 있음 (productId 0, name null)
- 서비스 4개 모두 application.yml 위쪽에: `spring.jackson.mapper.allow-final-fields-as-mutators: true`
  - composite: ProductAggregate(POST 본문), HttpErrorInfo가 final
  - product·recommendation·review: 7장 `Event`가 final. 없으면 RabbitMQ/Kafka로 받은 이벤트의 eventType이
    null이 되어 MessageProcessorConfig의 switch에서 NPE. 저자 테스트는 Consumer에 객체를 직접 넣어서
    이 문제를 잡지 못하고 Docker 실행 때만 드러난다. (DLQ 토픽 error.* 이 비어 있으면 정상)
- 6장의 RestTemplate(JsonMapper) 빈은 7장에서 RestTemplate과 함께 삭제했다.

## 저자 파일을 통째로 복사할 때 다시 확인할 것 (6·7·9·10장에서 실제로 덮어써짐)
- build.gradle: springCloudVersion, test starter 이름, Testcontainers 모듈명, mongodb-reactive(-test),
  composite의 starter-webclient와 stream-test-binder
- *ApplicationTests, MessagingTests: `@AutoConfigureWebTestClient`, `UNPROCESSABLE_CONTENT`
- ProductCompositeIntegration: `tools.jackson` import 2줄, `JacksonException`, `UNPROCESSABLE_CONTENT`,
  `org.springframework.boot.health.contributor.Health`
  (Jackson 2가 classpath에 있어서 fasterxml로도 컴파일되지만 실행 시 ObjectMapper 빈이 없어 실패)
- HealthCheckConfiguration: health.contributor import 3줄
- composite yml: `spring.web.error.include-message`, `allow-final-fields-as-mutators`.
  뒷부분만 바꿀 때 앞쪽 springdoc/api 구역을 지우지 않게 주의
  (지우면 `Could not resolve placeholder 'api.common.version'`)
- core 서비스 yml: `spring.mongodb` (기본 + docker 구역), `allow-final-fields-as-mutators`
- api: VS Code 자동 import가 `io.swagger.v3.oas.annotations.parameters.RequestBody`를 넣으면 Spring의
  `@RequestBody`가 무시되어 빈 객체가 들어온다(key 0). Spring 것은 `org.springframework.web.bind.annotation.*`
- spring-cloud/eureka-server, gateway: 폴더째 복사하면 `settings.gradle`이 다시 들어오므로 삭제.
  build.gradle(4.0.9/21/2025.1.4, 테스트 의존성), Dockerfile(temurin 21), 루트 settings.gradle의 include
- gateway yml: host_route_* 3개의 uri와 SetPath (httpbin)
- docker-compose 3개: 저자 파일로 받으면 맨 끝 `httpbin` 서비스가 빠진다. 다시 추가할 것
- test-em-all.bash: 저자 파일로 받으면 우리만의 수정이 없는지 diff로 확인
- 11장 authorization-server: build.gradle 전체(위 표), Dockerfile temurin 21, settings.gradle 삭제,
  AuthorizationServerConfig import 2줄, 테스트의 AutoConfigureMockMvc import
- 11장 gateway·composite yml: 저자 파일로 덮으면 httpbin 라우트, `allow-final-fields-as-mutators`, `include-message`,
  `usePkceWithAuthorizationCodeGrant`가 빠진다. build.gradle은 resource-server 스타터 1줄로 대체한 것을 확인
- 11장 compose 3개: 저자 11장 파일로 받고 httpbin을 다시 붙였다 (저자 파일은 끝에 줄바꿈이 없음)
- 11장 test-em-all.bash: 저자 11장 파일을 그대로 사용 (우리만의 수정 없음)
- 12장 config-server: build.gradle(4.0.9/21/2025.1.4, `jar enabled=false`, junit-platform-launcher), Dockerfile temurin 21,
  모듈 안 settings.gradle 삭제 (include는 루트 settings.gradle에 `include ':spring-cloud:config-server'`)
- 12장 config-repo: 저자 파일 8개를 받은 뒤 우리 설정을 다시 넣었다. `application.yml`에 allow-final,
  product·recommendation·review·product-composite.yml에 `spring.web.error.include-message`,
  product·recommendation.yml에 `spring.mongodb`(기본 + docker 구역), gateway.yml에 httpbin 3개 라우트,
  product-composite.yml에 `usePkceWithAuthorizationCodeGrant`
  (allow-final은 application.yml에만 있으면 되지만 8개 모두에 넣어 둔 상태. 동작에는 문제없음)
- 12장 compose 3개: 저자 12장 파일로 받고 httpbin을 다시 붙였다. test-em-all.bash는 저자 12장 그대로
- 13장 이후 설정 변경은 서비스 yml이 아니라 `config-repo/`에서 한다. 저자 ChapterXX의 config-repo와 diff할 것
- 13장 ProductCompositeIntegration: 저자 파일로 덮지 말고 바뀐 부분만 넣는다
  (resilience4j import, ServiceUtil 필드·생성자, getProduct 어노테이션·UriComponentsBuilder, fallback 메서드).
  덮으면 `tools.jackson` import, `JacksonException`, `UNPROCESSABLE_CONTENT`, health.contributor가 되돌아간다
- 13장 config-repo/product-composite.yml: resilience4j 구역은 issuer-uri 다음, `logging:`과 첫 `---` 앞(기본 구역)에 넣는다
- 13장 test-em-all.bash: 저자 13장 파일 + 220~222행 기대값 3개 변경 (위 표)
- 14장 저자 파일 그대로 받아도 되는 것: composite `ProductCompositeServiceImpl`(우리 파일이 저자 13장과 같았음),
  tracing 3개(`ObservationRegistryConfig` import만 수정), `GatewayApplication`, `ReviewServiceApplication`
- 14장 덮으면 안 되는 것: product·recommendation Application(`spring.mongodb` 되돌아감, Hooks 2줄만 추가),
  ProductCompositeIntegration(생성자 `WebClient webClient`, `this.webClient = webClient` 2줄만),
  compose 3개(httpbin 빠짐, zipkin-server 블록만 httpbin 위에 추가)
- 14장 config-repo/application.yml: Zipkin 2줄은 첫 `---` 위(공통 구역). 아래쪽 `management.health.rabbit.enabled` 근처는 kafka 구역이다
- 확인 방법: 저자 ChapterXX와 diff, 서비스별 `./gradlew :microservices:xxx-service:build`, 전체 `./gradlew build`,
  `./test-em-all.bash start stop`

## Docker 실습 메모
- 각 서비스 설정(12장부터 `config-repo/{앱이름}.yml`) 끝에 docker 프로파일 구역이 있어야 한다. 없으면 컨테이너 안에서 7002 등으로 떠서
  `curl: (56) Recv failure: Connection reset by peer`가 난다.
- 12장부터 config-repo만 바꿨다면 이미지 재빌드는 필요 없다(볼륨으로 연결). 해당 서비스만 다시 시작한다: `docker compose restart product`.
  Java 코드, bootstrap.yml, build.gradle을 바꿨다면 아래처럼 jar와 이미지를 다시 빌드한다.
- 코드나 yml을 바꾸면 jar와 이미지를 모두 다시 빌드해야 반영된다.
  ```bash
  ./gradlew build
  docker compose build
  ./test-em-all.bash start stop
  ```
- `test-em-all.bash start`는 `docker compose up -d`만 하므로 이미지를 다시 빌드하지 않는다.
  확인: `docker image ls | grep msa`의 생성 시간과 build/libs의 jar 시간 비교
- 7장 test-em-all: `/actuator/health`로 시작 대기, POST/DELETE는 202 기대, `waitForMessageProcessing`이 재시도.
  "Test FAILED ... WILL ABORT" 다음에 retry #1이 이어지면 최종 일관성 때문의 정상 재시도다.
- 9·10장 test-em-all: Eureka 확인은 Gateway 경유 `$HOST:$PORT/eureka/api/apps`, 등록 수 5
  (gateway, product-composite, product, recommendation, review). `/actuator/health`는 이제 Gateway의 health이며,
  4개 서비스가 Eureka에 등록되고 응답해야 UP이 되므로 시작 대기가 길어질 수 있다.
- Gateway 관찰 명령 (`./test-em-all.bash start`로 띄운 상태에서, 11장부터 https/8443과 `-k`)
  ```bash
  curl -k https://localhost:8443/actuator/gateway/routes -s | jq '.[] | {"\(.route_id)": "\(.uri)"}' | grep -v '{\|}'
  docker compose logs -f --tail=0 gateway
  curl -H "accept:application/json" -k https://u:p@localhost:8443/eureka/api/apps -s | jq -r '.applications.application[].instance[].instanceId'
  curl -k https://localhost:8443/headerrouting -H "Host: i.feel.lucky:8080" -i   # 200 (im.a.teapot → 418, Host 없음 → 501)
  ```
  - 교재의 `curl http://httpstat.us/200 -i`는 `curl https://httpbin.org/status/200 -i`로 대신한다.
  - hosts 실습은 WSL의 /etc/hosts를 고친다: `sudo bash -c "echo '127.0.0.1 i.feel.lucky im.a.teapot' >> /etc/hosts"`,
    끝나면 `sudo sed -i '/i.feel.lucky im.a.teapot/d' /etc/hosts` (WSL 재시작 시에도 다시 생성됨)
- 11장 test-em-all: PORT 8443, https + `-k`, writer 토큰(client_credentials)으로 호출, Eureka 등록 수 6(auth-server 추가),
  토큰 없음 401, reader 토큰으로 DELETE 403. 시작 순서가 auth-server(healthy) → composite·gateway라 대기가 길다(retry 10회 정도).
- OAuth 실습 명령 (11장)
  ```bash
  TOKEN=$(curl -sk https://writer:secret-writer@localhost:8443/oauth2/token -d grant_type=client_credentials -d scope="product:read product:write" | jq -r .access_token)
  READER_TOKEN=$(curl -sk https://reader:secret-reader@localhost:8443/oauth2/token -d grant_type=client_credentials -d scope="product:read" | jq -r .access_token)
  curl -k https://localhost:8443/product-composite/1 -H "Authorization: Bearer $READER_TOKEN" -i                # 200
  curl -k https://localhost:8443/product-composite/999 -H "Authorization: Bearer $READER_TOKEN" -X DELETE -i    # 403
  docker compose logs -f product-composite | grep "Authorization info"
  ```
  - 인가 코드 실습은 PKCE가 필수다. 같은 터미널에서 VERIFIER/CHALLENGE를 만들고, authorize 주소에
    `&code_challenge=$CHALLENGE&code_challenge_method=S256`, 토큰 교환 curl에 `-d code_verifier=$VERIFIER`를 붙인다.
    ```bash
    VERIFIER=$(head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=')
    CHALLENGE=$(printf %s "$VERIFIER" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
    ```
  - `invalid_grant`: VERIFIER가 그 시도의 짝이 아님(다른 터미널, ① 재실행), code 만료(약 5분), code 재사용. ①부터 다시.
  - redirect 후 "사이트에 연결할 수 없음"은 정상. 주소창의 `code=` 뒤부터 `&state` 앞까지 복사.
  - 교재 오타: 주소의 `https:/localhost`, curl의 `my.redirect.url`(→ `.uri`), "read 스코프만 가진 토큰"은 reader/product:read
  - Swagger UI: 새 시크릿 창에서 Authorize → select all → u/p → 동의 → Close. 인증서 경고는 "고급 → 계속"
- 로컬(java -jar)로 gateway·composite를 띄우면 토큰 검증이 실패한다. auth-server가 issuer를 `http://auth-server:9999`로
  고정해서, localhost로 접속한 쪽과 issuer가 다르기 때문이다(`did not match`). 토큰 관련 확인은 Docker에서 한다.
- auth-server는 시작할 때마다 서명 키를 새로 만든다. auth-server만 재시작하면 이전 토큰은 모두 401이 된다.
- 12장 test-em-all: 11장 내용 + Gateway 경유 `/config/product/docker` 200, encrypt/decrypt `hello-world` 비교.
  Eureka 등록 수는 6 그대로 (config-server는 Eureka에 등록하지 않음).
  시작 직후 `Could not locate PropertySource ... retrying`은 구성 서버를 기다리는 정상 재시도다.
- 구성 서버 실습 명령 (12장, 반드시 `~/msa`에서 실행. 다른 곳이면 `.env`를 못 읽어 `variable is not set` 경고)
  ```bash
  curl -k https://dev-usr:dev-pwd@localhost:8443/config/product/docker -s | jq -r '.propertySources[].name'   # 위가 우선
  curl -k https://dev-usr:dev-pwd@localhost:8443/config/product-docker.yml -s                                 # 합쳐진 최종 설정
  ENC=$(curl -k https://dev-usr:dev-pwd@localhost:8443/config/encrypt --data-urlencode "hello world" -s)
  curl -k https://dev-usr:dev-pwd@localhost:8443/config/decrypt -d $ENC -s ; echo
  curl -k https://dev-usr:dev-pwd@localhost:8443/config/review/docker -s | jq '.propertySources[].source | with_entries(select(.key | test("password")))'
  curl -k https://localhost:8443/config/product/docker -i -s | head -1                                        # 401
  ```
- 13장 test-em-all: 12장 내용 + 서킷 브레이커 검증 (delay=3으로 OPEN, fallback, 13 → 404, 10초 후 HALF_OPEN, 정상 3회 → CLOSED).
  `SKIP_CB_TESTS=true ./test-em-all.bash`로 이 부분을 건너뛸 수 있다
- 복원력 실습 명령 (13장, `./test-em-all.bash start`로 띄우고 TOKEN을 발급한 같은 터미널에서)
  ```bash
  cbstate() { docker compose exec -T product-composite wget -qO - http://product-composite:8080/actuator/health | jq -r .components.circuitBreakers.details.product.details.state; }
  call() { curl -k "https://localhost:8443/product-composite/$1" -H "Authorization: Bearer $TOKEN" -s -w "  → HTTP %{http_code}, %{time_total}s\n"; }
  call "1?delay=3"            # 2초에 500 (TimeLimiter). 반복하면 OPEN
  call 1                      # OPEN이면 즉시 "Fallback product1"
  sleep 10; cbstate           # HALF_OPEN
  for i in $(seq 1 10); do call "1?faultPercent=25" | tail -1; done   # 1초·2초짜리 응답 = 재시도
  docker compose exec -T product-composite wget -qO - http://product-composite:8080/actuator/circuitbreakerevents/product/STATE_TRANSITION | jq -r '.circuitBreakerEvents[] | "\(.creationTime[11:19])  \(.stateTransition)"'
  docker compose exec -T product-composite wget -qO - http://product-composite:8080/actuator/retryevents | jq -r '.retryEvents[] | "\(.type) \(.numberOfAttempts)"'
  docker compose logs product --since 2m | grep "Bad luck"
  ```
  - actuator 시간은 UTC (한국 시간 -9시간)
  - `faultPercent=100`을 반복하면 재시도 후 500이 쌓여 OPEN이 된다 (500은 ignoreExceptions에 없음)
- 셸 스크립트 문법 확인: `bash -n test-em-all.bash` (실행하지 않고 검사. `unexpected EOF ... matching '"'`는 따옴표 짝 문제)
- 14장 test-em-all: 변경 없음 (13장 파일 그대로). 통과하면 추적 추가로 기존 기능이 깨지지 않았다는 뜻
- 분산 추적 실습 명령 (14장, `./test-em-all.bash start` 후 같은 터미널, TOKEN·call은 13장과 같음)
  ```bash
  curl -s localhost:9411/api/v2/services | jq        # 7개: auth-server, eureka-server, gateway, product, product-composite, recommendation, review
  lasttrace() { docker compose logs product-composite --since 30s | grep -o "\[product-composite,[0-9a-f]\{32\}" | tail -1 | cut -d, -f2; }
  call 1; TID=$(lasttrace); echo "http://localhost:9411/zipkin/traces/$TID"   # 브라우저로 열기
  docker compose logs | grep $TID                    # 같은 요청의 로그만 (서비스 4개)
  ```
  - Zipkin 화면: `http://localhost:9411` (gateway를 거치지 않음). 필터 주소 예:
    `/zipkin/?serviceName=product-composite&lookback=15m`, `/zipkin/?annotationQuery=productId%3D1`, 관계도 `/zipkin/dependency`
  - 확인한 것: GET은 gateway → composite("product info" span, productId·build.version 태그) → product/recommendation/review 병렬,
    review 아래 JDBC span(connection/query/result-set). POST/DELETE는 `xxx-out-0 send` → RabbitMQ → `receive` → `messageprocessor process`까지 trace 하나.
    에러는 빨간색. `delay=3`은 composite CLIENT span 2초(TimeLimiter), product SERVER span 3초
  - span은 몇 초씩 묶어서 전송된다. 상세 화면이 비거나 일부 서비스가 빠지면 5초 뒤 새로고침
  - 요청 없이도 쌓이는 trace: `xxx: http put`(Eureka 하트비트), `xxx: http get`(레지스트리 조회), `auth-server: /actuator/health`(Docker healthcheck)
  - 추적 헤더 보기: config-repo/product-composite.yml 공통 구역에 `spring.codec.log-request-details: true`,
    `logging.level.org.springframework.web.reactive.function.client.ExchangeFunctions: TRACE` → `docker compose restart product-composite`
    → 로그에서 `traceparent:"00-{traceId}-{부모spanId}-01"` 확인. 끝나면 두 줄 삭제 후 다시 restart
  - Zipkin은 메모리 저장(`STORAGE_TYPE=mem`)이라 재시작하면 기록이 사라진다. Zipkin이 꺼져도 서비스는 정상 동작하고 `Failed to export spans` 경고만 남는다

- 파티션/Kafka 실행 (같은 터미널에서)
  ```bash
  export COMPOSE_FILE=docker-compose-partitions.yml   # 또는 docker-compose-kafka.yml
  docker compose build
  ./test-em-all.bash start stop
  unset COMPOSE_FILE                                   # 안 하면 계속 그 파일로 실행됨
  ```
  지금 떠 있는 compose 파일 확인: `docker compose ls`
- 관찰 명령 (`./test-em-all.bash start`로 띄운 상태에서)
  ```bash
  docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers   # 관리 화면: localhost:15672
  docker compose logs product product-p1 | grep "Process message"
  docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic products
  docker compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic products --from-beginning --timeout-ms 10000 [--partition 0]
  ```
  - console-consumer는 --partition 없이 쓰면 그룹 참여에 수 초 걸리므로 timeout을 넉넉히 준다.
  - 테스트 데이터 productId 1, 113, 213은 모두 홀수라 `hash % 2 = 1` → 전부 파티션 1로 간다.
    파티션 0을 보려면 짝수 ID로 POST.
  - 파티션 → 인스턴스 배정: RabbitMQ는 instanceIndex대로 고정(파티션 1 = -p1).
    Kafka는 autoRebalanceEnabled(기본 true) 때문에 Kafka가 배정해서 instanceIndex와 무관하고 실행마다 바뀔 수 있다.
    실제 배정 확인: kafka-consumer-groups --describe --group productsGroup (HOST 열)
  - Kafka의 `error.<topic>.<group>` 토픽은 DLQ (`enableDlq: true`). 비어 있으면 처리 실패 없음.
- Kafka 구성은 컨테이너 12개, mem_limit 합계 약 6.5GB (14장 zipkin-server 1GB 추가). 137로 죽으면 메모리 부족.
- 로컬(java -jar)로 테스트할 때: `PORT=7001 ./test-em-all.bash`
- 빌드 명령 경로는 `:microservices:product-service:build` (s 주의). `./gradlew build/`처럼 슬래시가 붙으면 실패.
- WSL에서 docker가 없으면 Docker Desktop의 WSL Integration에서 Ubuntu를 켠다.
- 3306 포트 충돌(`ports are not available: exposing port TCP 0.0.0.0:3306`):
  Windows의 MySQL80 서비스가 3306을 사용 중이다. 관리자 PowerShell에서 `net stop MySQL80`
  (재부팅하면 다시 켜짐. 계속 끄려면 services.msc에서 시작 유형을 수동으로)
- DB 내용 확인:
  ```bash
  docker compose exec mongodb mongosh product-db --quiet --eval "db.products.find()"
  docker compose exec mysql mysql -uuser -p review-db -e "select * from reviews"   # 비밀번호 pwd
  ```

## 테스트 메모
- 테스트 클래스에는 main이 없다. JUnit이 @Test 메서드를 실행하고 @SpringBootTest가 앱을 띄운다.
- 테스트는 Testcontainers가 MongoDB/MySQL 컨테이너를 띄우므로 Docker Desktop이 켜져 있어야 한다.
- 7장 composite의 MessagingTests는 test-binder(OutputDestination)로 발행된 이벤트를 확인한다. 브로커 불필요.
- 빌드 출력의 `OpenJDK ... Sharing is only supported for boot loader classes` 경고와
  `GracefulShutdown` 로그는 정상이다.
- `MySqlTestBase`의 raw type, Resource leak 경고는 저자 코드에서 나오는 노란 줄로 무시해도 된다.

## VS Code 문제 해결
- 터미널 빌드는 되는데 빨간 줄이 남으면 VS Code가 이전 프로젝트 정보를 쓰는 경우가 많다.
  (7장 MessagingTests의 test-binder import에서 실제로 발생)
- build.gradle 수정 후: Java: Reload Projects 실행
- 그래도 남으면: Java: Clean Java Language Server Workspace 실행 후 Reload and Delete 선택

## 이후 챕터 주의점
- 저자 ChapterXX와 diff해서 바뀐 파일 목록부터 확인하고, 위 "통째로 복사할 때 다시 확인할 것"을 적용한다.
- Spring Cloud 의존성(8장 이후 Eureka, Gateway, Config 등)은 springCloudVersion 2025.1.4 BOM 기준으로 확인한다.
- final 관련 yml 설정은 유지: HttpErrorInfo가 23장까지 final, Event도 final
