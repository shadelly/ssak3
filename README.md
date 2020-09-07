# Ssak3 - 청소대행 서비스

# Table of contents
- [ssak3](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현](#구현)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
  - [운영](#운영)
    - [CI/CD 설정](#cicd-설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)
    - [ConfigMap 사용](#configmap-사용)
  - [신규 개발 조직의 추가](#신규-개발-조직의-추가)

# 서비스 시나리오
  
## 기능적 요구사항
1. 고객이 청소를 요청한다
2. 청소업체가 청소를 완료한다
3. 청소가 완료되면, 고객에게 완료되었다고 전달한다 (Async, 알림서비스)
4. 고객이 결제한다(Sync, 결제서비스)
5. 결제가 완료되면, 결제 & 예약 내용을 청소업체에게 전달한다 (Async, 알림서비스)
6. 고객은 본인의 예약 내용 및 상태를 조회한다
7. 고객은 본인의 예약을 취소할 수 있다
8. 예약이 취소되면, 결제를 취소한다. (Async, 결제서비스)
9. 결제가 취소되면, 결제 취소 내용을 청소업체에게 전달한다 (Async, 알림서비스)

## 비기능적 요구사항
### 1. 트랜잭션
- 결제가 되지 않은 예약건은 아예 거래가 성립되지 않아야 한다 → Sync 호출 
### 2. 장애격리
- 통지(알림) 기능이 수행되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다 - Async (event-driven), Eventual Consistency
- 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다 → Circuit breaker, fallback
### 3. 성능
- 고객과 청소업체가 자주 예약관리에서 확인할 수 있는 상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다 → CQRS
- 상태가 바뀔때마다 카톡 등으로 알림을 줄 수 있어야 한다 → Event driven

# 분석/설계

## AS-IS 조직 (Horizontally-Aligned)
  ![03](https://user-images.githubusercontent.com/69634194/92385495-f3e68200-f14c-11ea-9ca0-c27cc85c986d.png)


## TO-BE 조직 (Vertically-Aligned)
  ![04](https://user-images.githubusercontent.com/69634194/92385625-30b27900-f14d-11ea-9e39-1fbe1bf303d5.png)

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과 : http://www.msaez.io/#/storming/k1eXHY4YSrSFKU3UpQTDRHUvSS23/every/f5d0809e09167fd49a1a95acfc9dd0d2/-MGcF3GTrAc5KsEkYr8b

### 이벤트 도출
  ![06](https://user-images.githubusercontent.com/69634194/92385630-327c3c80-f14d-11ea-8dfe-67e160446c67.png)
  
### 부적격 이벤트 탈락
  ![07](https://user-images.githubusercontent.com/69634194/92385696-49bb2a00-f14d-11ea-950e-a7fee81c4b8c.png)

* 과정중 도출된 잘못된 도메인 이벤트들을 걸러내는 작업을 수행함
  - 결재버튼이 클릭됨, 청소요청 검색됨, 예약정보조회됨 :  UI 의 이벤트이지, 업무적인 의미의 이벤트가 아니라서 제외

### 액터, 커맨드 부착하여 읽기 좋게
  ![08](https://user-images.githubusercontent.com/69634194/92385700-4aec5700-f14d-11ea-9844-286be9049d6a.png)
  
### 어그리게잇으로 묶기
  ![09](https://user-images.githubusercontent.com/69634194/92385703-4b84ed80-f14d-11ea-8c45-55e7af269eb6.png)

* 청소업체의 청소결과, 예약의 예약관리, 결제의 결제이력, 알림의 알림이력, 마이페이지는 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌
   
### 바운디드 컨텍스트로 묶기
  ![10](https://user-images.githubusercontent.com/69634194/92385705-4c1d8400-f14d-11ea-9c89-ae82b5c60900.png)

* 도메인 서열 분리 
  - Core Domain: 예약 
     - 없어서는 안될 핵심 서비스이며, 연간 Up-time SLA 수준을 99.999% 목표, 배포주기는 예약의 경우 1주일 1회 미만, 청소업체의 경우 1개월 1회 미만
  - Supporting Domain: 알림, 마이페이지 
     - 경쟁력을 내기위한 서비스이며, SLA 수준은 연간 60% 이상 uptime 목표, 배포주기는 각 팀의 자율이나 표준 스프린트 주기가 1주일 이므로 1주일 1회 이상을 기준으로 함.
  - General Domain:   결제 
      - 결제서비스로 3rd Party 외부 서비스를 사용하는 것이 경쟁력이 높음 
  
  
### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)
  ![11](https://user-images.githubusercontent.com/69634194/92385708-4c1d8400-f14d-11ea-99f3-10f79ce24f50.png)
    
### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)
  ![12](https://user-images.githubusercontent.com/69634194/92385709-4cb61a80-f14d-11ea-9389-c12ab4ef48e2.png)
    
### 기능적 요구사항 검증
1. 요구사항별로 모든 나래이션이 가능한지 검증함
2. 기능 요구사항별로 패스 표시

  ![13](https://user-images.githubusercontent.com/69634194/92385711-4d4eb100-f14d-11ea-935d-5b731ba3ab63.png)

### 시나리오 검증
  ![14](https://user-images.githubusercontent.com/69634194/92385712-4de74780-f14d-11ea-8c83-a548b0736f28.png)
1. 고객이 청소를 요청한다
2. 고객이 결제한다
3. 결제가 완료되면, 결제 & 예약 내용을 청소업체에게 전달한다 (Async, 알림서비스)
  ![15](https://user-images.githubusercontent.com/69634194/92385714-4e7fde00-f14d-11ea-9c34-053742fa9d76.png)
4. 고객은 본인의 예약 내용 및 상태를 조회한다
5. 고객은 본인의 예약을 취소할 수 있다
6. 예약이 취소되면, 결제를 취소한다 (Async, 결제서비스)
7. 결제가 취소되면, 결제 취소 내용을 청소업체에게 전달한다 (Async, 알림서비스)
  ![16](https://user-images.githubusercontent.com/69634194/92385715-4f187480-f14d-11ea-8728-1fb7201b9354.png)
8. 청소업체가 청소를 완료한다 
9. 청소가 완료되면, 고객에게 완료되었다고 전달한다 (Async, 알림서비스)

### 비기능 요구사항 검증
  ![17](https://user-images.githubusercontent.com/69634194/92387512-a409ba00-f150-11ea-994c-68282cbc2856.png)
1. 예약에 대해서는 결제가 처리되어야만 예약 처리하고 장애격리를 위해 CB를 설치함 (트랜잭션 > 1, 장애격리 > 2)
2. 예약, 결제 관련 이벤트를 마이페이지에서 수신하여 View Table 을 구성 (CQRS) (성능 > 1)

* 마이크로 서비스를 넘나드는 시나리오에 대한 트랜잭션 처리
    - 청소 예약시 결제처리
      - 결제가 완료되지 않은 예약은 절대 받지 않는다에 따라, ACID 트랜잭션 적용. 예약 완료시 결제처리에 대해서는 Request-Response 방식 처리
    - 예약 완료시 알림 처리
      - 예약에서 알림 마이크로서비스로 예약 완료 내용을 전달되는 과정에 있어서 알림 마이크로서비스가 별도의 배포주기를 가지기 때문에 Eventual Consistency 방식으로 트랜잭션 처리함.
    - 나머지 모든 inter-microservice 트랜잭션
      - 예약상태, 예약취소 등 모든 이벤트에 대해 알림 처리하는 등, 데이터 일관성의 시점이 크리티컬하지 않은 모든 경우가 대부분이라 판단, Eventual Consistency 를 기본으로 채택함.
    
## 헥사고날 아키텍처
  ![18](https://user-images.githubusercontent.com/69634194/92385717-4fb10b00-f14d-11ea-9342-3a1a92727032.png)
  * Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
  * 호출관계에서 PubSub 과 Req/Resp 를 구분함

# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 배포는 아래와 같이 수행한다.

## DDD 의 적용

## 동기식 호출 과 Fallback 처리

## 비동기식 호출 과 Eventual Consistency

# 운영

## CI/CD 설정

## 동기식 호출 / 서킷 브레이킹 / 장애격리

## 오토스케일 아웃

## 무정지 재배포

## ConfigMap 사용

# 신규 개발 조직의 추가

