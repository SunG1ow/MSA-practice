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

## 현재 상태 (2026-11-02, 3장 진행 중)
- 구현 완료: api, util, product-service, product-composite-service
- 구현 전: review-service, recommendation-service
  - 필요한 것: project(':api'), project(':util') 의존성, @ComponentScan("se.magnus"),
    services 폴더의 구현 클래스
- 포트: composite 7001, product 7002, recommendation 7003, review 7004

## 저자 코드와 다르게 한 부분
- 서비스별 settings.gradle 삭제 (VS Code의 ':api' 인식 오류 해결)
- HttpStatus.UNPROCESSABLE_ENTITY -> UNPROCESSABLE_CONTENT
  (resolve(422)가 CONTENT를 반환하므로 switch의 case도 변경 필수)
- import tools.jackson.databind.ObjectMapper, catch (JacksonException ...)
- 테스트 의존성은 Boot 4용 *-webflux-test, *-actuator-test 사용

## 미적용 확인 필요: Jackson 3의 final 필드 문제
증상: composite 응답에서 productId가 0, name이 null
해결 (DTO의 final 유지, 복사본에서 200/422/404 확인 완료):
1. composite application.yml:
   spring.jackson.mapper.allow-final-fields-as-mutators: true
2. ProductCompositeServiceApplication의 RestTemplate 빈:
   RestTemplate restTemplate(JsonMapper mapper) {
     return new RestTemplate(List.of(new JacksonJsonHttpMessageConverter(mapper)));
   }

## 이후 챕터 주의점 (저자 코드 비교 기준, Boot 4 실행은 미확인)
- 6장: Product, Recommendation, Review에서 final이 제거되고 setter 추가
- 7장: RestTemplate 제거, WebClient로 전환. restTemplate() 코드도 함께 삭제
- yml 설정은 유지: HttpErrorInfo가 23장까지 final
- 7장 Event도 final이고 fasterxml import 사용. 메시지 수신 서비스에서 확인 필요
- 교재 yml을 통째로 덮어쓸 때 위 설정이 사라지지 않게 주의
