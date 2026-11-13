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

## 현재 상태 (2026-11-13, 7장 완료)
- 3장: api, util, product, recommendation, review, product-composite 서비스 구현
- 4장: Dockerfile 4개, docker-compose.yml, docker 프로파일, test-em-all.bash(start/stop)
- 5장: springdoc-openapi 적용, Swagger UI 및 OpenAPI 문서 테스트
- 6장: 영속성 추가 (product·recommendation → MongoDB, review → MySQL)
- 7장: 리액티브 전환 + 이벤트 기반 비동기
  - 조회(GET): 논블로킹 동기 (WebClient + Mono.zip, 리액티브 Mongo, review는 JPA + jdbcScheduler)
  - 생성/삭제: composite가 StreamBridge로 이벤트 발행 → core 서비스의 MessageProcessorConfig가 소비 (202 Accepted)
  - ./gradlew build 통과, test-em-all 통과: 기본(RabbitMQ), partitions, kafka 3가지 모두
- 포트
  - 로컬(java -jar): composite 7001, product 7002, recommendation 7003, review 7004
  - docker 프로파일: 모든 서비스 8080, 외부 공개는 composite 8080:8080
  - DB/브로커: mongodb 27017, mysql 3306, rabbitmq 5672(관리 화면 15672, guest/guest), kafka 9092, zookeeper 2181

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

## Jackson 3의 final 필드 대응 (적용 완료)
증상: final 필드 객체를 JSON에서 읽을 때 값이 비어 있음 (productId 0, name null)
- 서비스 4개 모두 application.yml 위쪽에: `spring.jackson.mapper.allow-final-fields-as-mutators: true`
  - composite: ProductAggregate(POST 본문), HttpErrorInfo가 final
  - product·recommendation·review: 7장 `Event`가 final. 없으면 RabbitMQ/Kafka로 받은 이벤트의 eventType이
    null이 되어 MessageProcessorConfig의 switch에서 NPE. 저자 테스트는 Consumer에 객체를 직접 넣어서
    이 문제를 잡지 못하고 Docker 실행 때만 드러난다. (DLQ 토픽 error.* 이 비어 있으면 정상)
- 6장의 RestTemplate(JsonMapper) 빈은 7장에서 RestTemplate과 함께 삭제했다.

## 저자 파일을 통째로 복사할 때 다시 확인할 것 (6·7장에서 실제로 덮어써짐)
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
- 확인 방법: 저자 ChapterXX와 diff, 서비스별 `./gradlew :microservices:xxx-service:build`, 전체 `./gradlew build`,
  `./test-em-all.bash start stop`

## Docker 실습 메모
- 각 서비스 yml 끝에 docker 프로파일 구역이 있어야 한다. 없으면 컨테이너 안에서 7002 등으로 떠서
  `curl: (56) Recv failure: Connection reset by peer`가 난다.
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
- Kafka 구성은 컨테이너 11개, mem_limit 합계 약 5.5GB. 137로 죽으면 메모리 부족.
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
