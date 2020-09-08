# Ssak3 - 청소대행 서비스

# Table of contents
- [ssak3]
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석/설계)
  - [구현](#구현)
    - [DDD 의 적용](#DDD-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출과-Eventual-Consistency)
  - [운영](#운영)
    - [CI/CD 설정](#CI/CD-설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출-/-서킷-브레이킹-/-장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)
    - [ConfigMap 사용](#ConfigMap-사용)
  - [신규 개발 조직의 추가](#신규-개발-조직의-추가)

# 서비스 시나리오
  
## 기능적 요구사항
1. 고객이 청소를 요청하면 결제가 완료된다(Sync, 결제서비스)
2. 청소업체가 청소를 완료한다
3. 청소가 완료되면, 고객에게 완료되었다고 전달한다 (Async, 알림서비스)
4. 결제가 완료되면, 결제 & 예약 내용을 청소업체에게 전달한다 (Async, 알림서비스)
5. 고객은 본인의 예약 내용 및 상태를 조회한다
6. 고객은 본인의 예약을 취소할 수 있다
7. 예약이 취소되면, 결제를 취소한다. (Async, 결제서비스)
8. 결제가 취소되면, 결제 취소 내용을 청소업체에게 전달한다 (Async, 알림서비스)

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
  ![10](https://user-images.githubusercontent.com/36612394/92478750-95d59f80-f21d-11ea-813e-3e4e3050b7f5.png)
  
* 도메인 서열 분리 
  - Core Domain: 예약 
     - 없어서는 안될 핵심 서비스이며, 연간 Up-time SLA 수준을 99.999% 목표, 배포주기는 예약의 경우 1주일 1회 미만, 청소업체의 경우 1개월 1회 미만
  - Supporting Domain: 알림, 마이페이지 
     - 경쟁력을 내기위한 서비스이며, SLA 수준은 연간 60% 이상 uptime 목표, 배포주기는 각 팀의 자율이나 표준 스프린트 주기가 1주일 이므로 1주일 1회 이상을 기준으로 함.
  - General Domain:   결제 
      - 결제서비스로 3rd Party 외부 서비스를 사용하는 것이 경쟁력이 높음 
  
  
### 폴리시 부착 
  ![11](https://user-images.githubusercontent.com/36612394/92478754-966e3600-f21d-11ea-81ae-60cce97e2551.png)
      
### 폴리시의 이동과 컨텍스트 매핑 (빨강은 Pub/Sub, 파랑은 Req/Resp)
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

```console
```

## DDD 의 적용
* 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 결제 마이크로서비스).
  - 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용할 수 있지만, 일부 구현에 있어서 영문이 아닌 경우는 실행이 불가능한 경우가 있다 Maven pom.xml, Kafka의 topic id, FeignClient 의 서비스 id 등은 한글로 식별자를 사용하는 경우 오류가 발생하는 것을 확인하였다)
  - 최종적으로는 모두 영문을 사용하였으며, 이는 잠재적인 오류 발생 가능성을 차단하고 향후 확장되는 다양한 서비스들 간에 영향도를 최소화하기 위함이다.
```console
package CleaningServiceYD;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Payment_table")
public class Payment {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long requestId;
    private Integer price;
    private String status;

    @PostPersist
    public void onPostPersist(){

    	System.out.println("##### Payment onPostPersist : " + getStatus());

    	if("PaymentApproved".equals(getStatus())) {

        	PayConfirmed payConfirmed = new PayConfirmed();
            BeanUtils.copyProperties(this, payConfirmed);
            payConfirmed.setRequestId(getRequestId());
            payConfirmed.setStatus("PaymentCompleted");
            payConfirmed.publishAfterCommit();
        }

        else if("PaymentCancel".equals(getStatus())) {
        	PayCancelConfirmed payCancelConfirmed = new PayCancelConfirmed();
            BeanUtils.copyProperties(this, payCancelConfirmed);
            payCancelConfirmed.setRequestId(getRequestId());
            payCancelConfirmed.setStatus("PaymentCancelCompleted");
            payCancelConfirmed.publishAfterCommit();
        }

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }
    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package CleaningServiceYD;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface PaymentRepository extends PagingAndSortingRepository<Payment, Long>{


}
```

- siege 접속
```
kubectl exec -it siege -n cleaning -- /bin/bash
```
- (siege 에서) 적용 후 REST API 테스트 
```
# 청소 서비스 예약요청 처리
http POST http://reservation:8081/cleaningReservations requestDate=20200907 place=seoul status=ReservationApply price=2000 customerName=yeon

# 예약 상태 확인
http http://reservation:8081/reservations/1
```

## 폴리글랏 퍼시스턴스

  * 각 마이크로서비스의 특성에 따라 데이터 저장소를 RDB, DocumentDB/NoSQL 등 다양하게 사용할 수 있지만, 시간적/환경적 특성상 모두 H2 메모리DB를 적용하였다.

## 폴리글랏 프로그래밍
  
  * 각 마이크로서비스의 특성에 따라 다양한 프로그래밍 언어를 사용하여 구현할 수 있지만, 시간적/환경적 특성상 Java를 이용하여 구현하였다.

## 동기식 호출 과 Fallback 처리
분석단계에서의 조건 중 하나로 예약->결제 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제 서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
```console
@FeignClient(name="Payment", url="${api.url.payment}")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void payRequest(@RequestBody Payment payment);

}
```
- 예약을 받은 직후(@PostPersist) 결제가 완료되도록 처리
```
@Entity
@Table(name="CleaningReservation_table")
public class CleaningReservation {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String requestDate;
    private String place;
    private String status;
    private Integer price;
    private String customerName;

    @PostPersist
    public void onPostPersist(){
        // 예약시 결제까지 트랜잭션을 통합을 위해 결제 서비스 직접 호출
    	CleaningServiceYD.external.Payment payment = new CleaningServiceYD.external.Payment();
        payment.setRequestId(getId());
        payment.setPrice(getPrice());
        payment.setStatus("PaymentApproved");

        try {
        	ReservationApplication.applicationContext.getBean(CleaningServiceYD.external.PaymentService.class)
            	.payRequest(payment);
        } catch(Exception e) {
        	throw new RuntimeException("PaymentApprove failed. Check your payment Service.");
        }

    }

```

- (##### 수정필요 ####) 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인
```
# 결제 서비스를 잠시 내려놓음
$ kubectl delete -f payment.yaml

NAME                           READY   STATUS    RESTARTS   AGE
cleaning-bf474f568-vxl8r       2/2     Running   0          137m
dashboard-7f7768bb5-7l8wr      2/2     Running   0          136m
gateway-6dfcbbc84f-rwnsh       2/2     Running   0          37m
message-69597f6864-mhwx7       2/2     Running   0          137m
reservation-775fc6574d-kddgd   2/2     Running   0          144m
siege                          2/2     Running   0          3h39m

# 예약처리 (siege 에서)
http POST http://reservation:8080/cleaningReservations requestDate=20200907 place=seoul status=ReservationApply price=250000 customerName=chae #Fail
http POST http://reservation:8080/cleaningReservations requestDate=20200909 place=pangyo status=ReservationApply price=300000 customerName=noh #Fail

# 예약처리 시 에러 내용
HTTP/1.1 500 Internal Server Error
content-type: application/json;charset=UTF-8
date: Tue, 08 Sep 2020 15:51:34 GMT
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 87

{
    "error": "Internal Server Error",
    "message": "Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction",
    "path": "/cleaningReservations",
    "status": 500,
    "timestamp": "2020-09-08T15:51:34.959+0000"
}

# 결제서비스 재기동전에 아래의 비동기식 호출 기능 점검 테스트 수행 (siege 에서)
http DELETE http://reservation:8080/reservations/1 #Success

# 결과
root@siege:/# http DELETE http://reservation:8080/reservations/1
HTTP/1.1 404 Not Found
content-type: application/hal+json;charset=UTF-8
date: Tue, 08 Sep 2020 15:52:46 GMT
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 16

{
    "error": "Not Found",
    "message": "No message available",
    "path": "/reservations/1",
    "status": 404,
    "timestamp": "2020-09-08T15:52:46.971+0000"
}

# 결제서비스 재기동
$ kubectl apply -f payment.yaml

NAME                           READY   STATUS    RESTARTS   AGE
cleaning-bf474f568-vxl8r       2/2     Running   0          147m
dashboard-7f7768bb5-7l8wr      2/2     Running   0          145m
gateway-6dfcbbc84f-rwnsh       2/2     Running   0          47m
message-69597f6864-mhwx7       2/2     Running   0          147m
payment-7749f7dc7c-kfjxb       2/2     Running   0          88s
reservation-775fc6574d-kddgd   2/2     Running   0          153m
siege                          2/2     Running   0          3h48m


# 예약처리 (siege 에서)
http POST http://reservation:8080/cleaningReservations requestDate=20200907 place=seoul status=ReservationApply price=250000 customerName=chae #Success
http POST http://reservation:8080/cleaningReservations requestDate=20200909 place=pangyo status=ReservationApply price=300000 customerName=noh #Success

# 처리결과
HTTP/1.1 201 Created
content-type: application/json;charset=UTF-8
date: Tue, 08 Sep 2020 15:58:28 GMT
location: http://reservation:8080/cleaningReservations/5
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 113

{
    "_links": {
        "cleaningReservation": {
            "href": "http://reservation:8080/cleaningReservations/5"
        },
        "self": {
            "href": "http://reservation:8080/cleaningReservations/5"
        }
    },
    "customerName": "noh",
    "place": "pangyo",
    "price": 300000,
    "requestDate": "20200909",
    "status": "ReservationApply"
}
```
- 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다 (서킷브레이커, 폴백 처리는 운영단계에서 설명)

## 비동기식 호출과 Eventual Consistency
- 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트
결제가 이루어진 후에 알림 처리는 동기식이 아니라 비 동기식으로 처리하여 알림 시스템의 처리를 위하여 예약이 블로킹 되지 않아도록 처리한다.
 
- 이를 위하여 예약관리, 결제관리에 기록을 남긴 후에 곧바로 완료되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
```console
@Entity
@Table(name="Payment_table")
public class Payment {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long requestId;
    private Integer price;
    private String status;

    @PostPersist
    public void onPostPersist(){

    	System.out.println("##### Payment onPostPersist : " + getStatus());

    	if("PaymentApproved".equals(getStatus())) {

        	PayConfirmed payConfirmed = new PayConfirmed();
            BeanUtils.copyProperties(this, payConfirmed);
            payConfirmed.setRequestId(getRequestId());
            payConfirmed.setStatus("PaymentCompleted");
            payConfirmed.publishAfterCommit();
        }

        else if("PaymentCancel".equals(getStatus())) {
        	PayCancelConfirmed payCancelConfirmed = new PayCancelConfirmed();
            BeanUtils.copyProperties(this, payCancelConfirmed);
            payCancelConfirmed.setRequestId(getRequestId());
            payCancelConfirmed.setStatus("PaymentCancelCompleted");
            payCancelConfirmed.publishAfterCommit();
        }

    }
    ...

```
- 알림 서비스에서는 결제승인, 청소완료, 결제취소 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다
```
@Service
public class PolicyHandler{

	@Autowired
    private MessageRepository messageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayConfirmed_MessageAlert(@Payload PayConfirmed payConfirmed){

        if(payConfirmed.isMe()){
        	Message message = new Message();

        	message.setRequestId(payConfirmed.getRequestId());
        	message.setStatus(payConfirmed.getStatus());

        	messageRepository.save(message);

            System.out.println("##### listener MessageAlert : " + payConfirmed.toJson());
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCleaningConfirmed_MessageAlert(@Payload CleaningConfirmed cleaningConfirmed){

        if(cleaningConfirmed.isMe()){
        	Message message = new Message();

        	message.setRequestId(cleaningConfirmed.getRequestId());
        	message.setStatus(cleaningConfirmed.getStatus());

        	messageRepository.save(message);

            System.out.println("##### listener MessageAlert : " + cleaningConfirmed.toJson());
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayCancelConfirmed_MessageAlert(@Payload PayCancelConfirmed payCancelConfirmed){

        if(payCancelConfirmed.isMe()){
        	Message message = new Message();

        	message.setRequestId(payCancelConfirmed.getRequestId());
        	message.setStatus(payCancelConfirmed.getStatus());

        	messageRepository.save(message);

            System.out.println("##### listener MessageAlert : " + payCancelConfirmed.toJson());
        }
    }


}
```
- 실제 구현을 하자면, 카톡 등으로 알림을 처리함
```
@Service
public class PolicyHandler{

    @Autowired
    private MessageRepository messageRepository;

    @StreamListener(KafkaProcessor.INPUT)
        public void wheneverPayConfirmed_MessageAlert(@Payload PayConfirmed payConfirmed){
    
            if(payConfirmed.isMe()){
            	Message message = new Message();
    
            	message.setRequestId(payConfirmed.getRequestId());
            	message.setStatus(payConfirmed.getStatus());
    
            	messageRepository.save(message);
            }
        }
 ```
알림 시스템은 예약/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 알림 시스템이 유지보수로 인해 잠시 내려간 상태라도 예약을 받는데 문제가 없다
(####수정필요####)
```
# 알림 서비스를 잠시 내려놓음
kubectl delete -f message.yaml

# 예약처리 (siege 에서)
http POST http://reservation:8080/cleaningReservations requestDate=20200907 place=seoul status=ReservationApply price=250000 customerName=chae #Success
http POST http://reservation:8080/cleaningReservations requestDate=20200909 place=pangyo status=ReservationApply price=300000 customerName=noh #Success

# 알림이력 확인 (siege 에서)
http http://message:8080/messages # 알림이력조회 불가

http: error: ConnectionError: HTTPConnectionPool(host='message', port=8080): Max retries exceeded with url: /messages (Caused by NewConnectionError('<urllib3.connection.HTTPConnection object at 0x7fae6595deb8>: Failed to establish a new connection: [Errno -2] Name or service not known')) while doing GET request to URL: http://message:8080/messages

# 알림 서비스 기동
kubectl apply -f message.yaml

# 알림이력 확인 (siege 에서)
http http://message:8080/messages # 알림이력조회

HTTP/1.1 200 OK
content-type: application/hal+json;charset=UTF-8
date: Tue, 08 Sep 2020 16:01:45 GMT
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 439

{
    "_embedded": {
        "messages": [
            {
                "_links": {
                    "message": {
                        "href": "http://message:8080/messages/1"
                    },
                    "self": {
                        "href": "http://message:8080/messages/1"
                    }
                },
                "requestId": 6,
                "status": "PaymentCompleted"
            },
            {
                "_links": {
                    "message": {
                        "href": "http://message:8080/messages/2"
                    },
                    "self": {
                        "href": "http://message:8080/messages/2"
                    }
                },
                "requestId": 7,
                "status": "PaymentCompleted"
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://message:8080/profile/messages"
        },
        "self": {
            "href": "http://message:8080/messages{?page,size,sort}",
            "templated": true
        }
    },
    "page": {
        "number": 0,
        "size": 20,
        "totalElements": 2,
        "totalPages": 1
    }
}
```

# 운영

## CI/CD 설정
  * 각 구현체들은 github의 각각의 source repository 에 구성
  * Image repository는 Azure 사용

## 동기식 호출 / 서킷 브레이킹 / 장애격리

### 방식1) 서킷 브레이킹 프레임워크의 선택: istio-injection + DestinationRule

* istio-injection 적용 (기 적용완료)
```
kubectl label namespace ssak3 istio-injection=enabled

# error: 'istio-injection' already has a value (enabled), and --overwrite is false
```
* 예약, 결제 서비스 모두 아무런 변경 없음

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시
```console
siege -v -c100 -t60S -r10 --content-type "application/json" 'http://reservation:8080/cleaningReservations POST {"customerName": "noh","price": 300000,"requestDate": "20200909","status": "ReservationApply"}'

HTTP/1.1 201     1.20 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     1.12 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     0.14 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     1.11 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     1.21 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     1.20 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     1.20 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     1.11 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     1.21 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 201     0.12 secs:     341 bytes ==> POST http://reservation:8080/cleaningReservations

Lifting the server siege...
Transactions:                   4719 hits
Availability:                 100.00 %
Elapsed time:                  59.14 secs
Data transferred:               1.53 MB
Response time:                  1.23 secs
Transaction rate:              79.79 trans/sec
Throughput:                     0.03 MB/sec
Concurrency:                   97.95
Successful transactions:        4719
Failed transactions:               0
Longest transaction:            7.29
Shortest transaction:           0.05
```
* 서킷 브레이킹을 위한 DestinationRule 적용
```
cd ssak3/yaml
kubectl apply -f payment_dr.yaml

# destinationrule.networking.istio.io/dr-payment created

HTTP/1.1 500     0.68 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 500     0.70 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 500     0.71 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 500     0.72 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 500     0.92 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 500     0.68 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 500     0.82 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
HTTP/1.1 500     0.71 secs:     262 bytes ==> POST http://reservation:8080/cleaningReservations
siege aborted due to excessive socket failure; you
can change the failure threshold in $HOME/.siegerc

Transactions:                     20 hits
Availability:                   1.75 %
Elapsed time:                   9.92 secs
Data transferred:               0.29 MB
Response time:                 48.04 secs
Transaction rate:               2.02 trans/sec
Throughput:                     0.03 MB/sec
Concurrency:                   96.85
Successful transactions:          20
Failed transactions:            1123
Longest transaction:            2.53
Shortest transaction:           0.04
```
## 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 함
* (istio injection 적용한 경우) istio injection 적용 해제
```
kubectl label namespace ssak3 istio-injection=disabled --overwrite

# namespace/ssak3 labeled

kubectl apply -f reservation.yaml
kubectl apply -f payment.yaml
```
- 결제서비스 배포시 resource 설정 적용되어 있음
```
    spec:
      containers:
          ...
          resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```

- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 3개까지 늘려준다
```console
kubectl autoscale deploy payment -n ssak3 --min=1 --max=3 --cpu-percent=15

# horizontalpodautoscaler.autoscaling/payment autoscaled

root@ssak3-vm:~/ssak3/yaml# kubectl get all -n ssak3
NAME                               READY   STATUS    RESTARTS   AGE
pod/cleaning-bf474f568-vxl8r       2/2     Running   0          3h5m
pod/dashboard-7f7768bb5-7l8wr      2/2     Running   0          3h3m
pod/gateway-6dfcbbc84f-rwnsh       2/2     Running   0          85m
pod/message-69597f6864-fjs69       2/2     Running   0          34m
pod/payment-7749f7dc7c-kfjxb       2/2     Running   0          39m
pod/reservation-775fc6574d-kddgd   2/2     Running   0          3h12m
pod/siege                          2/2     Running   0          4h27m

NAME                  TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)          AGE
service/cleaning      ClusterIP      10.0.150.114   <none>         8080/TCP         3h5m
service/dashboard     ClusterIP      10.0.69.44     <none>         8080/TCP         3h3m
service/gateway       LoadBalancer   10.0.56.218    20.196.72.75   8080:32642/TCP   85m
service/message       ClusterIP      10.0.255.90    <none>         8080/TCP         34m
service/payment       ClusterIP      10.0.64.167    <none>         8080/TCP         39m
service/reservation   ClusterIP      10.0.23.111    <none>         8080/TCP         3h12m

NAME                          READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/cleaning      1/1     1            1           3h5m
deployment.apps/dashboard     1/1     1            1           3h3m
deployment.apps/gateway       1/1     1            1           85m
deployment.apps/message       1/1     1            1           34m
deployment.apps/payment       1/1     1            1           39m
deployment.apps/reservation   1/1     1            1           3h12m

NAME                                     DESIRED   CURRENT   READY   AGE
replicaset.apps/cleaning-bf474f568       1         1         1       3h5m
replicaset.apps/dashboard-7f7768bb5      1         1         1       3h3m
replicaset.apps/gateway-6dfcbbc84f       1         1         1       85m
replicaset.apps/message-69597f6864       1         1         1       34m
replicaset.apps/payment-7749f7dc7c       1         1         1       39m
replicaset.apps/reservation-775fc6574d   1         1         1       3h12m

NAME                                          REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/payment   Deployment/payment   3%/15%    1         3         1          55s
```

- CB 에서 했던 방식대로 워크로드를 3분 동안 걸어준다.
```console
siege -v -c100 -t180S -r10 --content-type "application/json" 'http://reservation:8080/cleaningReservations POST {"customerName": "noh","price": 300000,"requestDate": "20200909","status": "ReservationApply"}'
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```console
kubectl get deploy payment -n ssak3 -w 

NAME      READY   UP-TO-DATE   AVAILABLE   AGE
payment   1/1     1            1           43m

# siege 부하 적용 후
root@ssak3-vm:/# kubectl get deploy payment -n ssak3 -w
NAME      READY   UP-TO-DATE   AVAILABLE   AGE
payment   1/1     1            1           43m
payment   1/3     1            1           44m
payment   1/3     1            1           44m
payment   1/3     3            1           44m
payment   2/3     3            2           46m
payment   3/3     3            3           46m
```
- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다.
```
Lifting the server siege...
Transactions:                  19309 hits
Availability:                 100.00 %
Elapsed time:                 179.75 secs
Data transferred:               6.31 MB
Response time:                  0.92 secs
Transaction rate:             107.42 trans/sec
Throughput:                     0.04 MB/sec
Concurrency:                   99.29
Successful transactions:       19309
Failed transactions:               0
Longest transaction:            7.33
Shortest transaction:           0.01
```

## 무정지 재배포
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함 (위의 시나리오에서 제거되었음)
- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```console
siege -v -c1 -t300S -r10 --content-type "application/json" 'http://reservation:8080/cleaningReservations'
```

## ConfigMap 사용
- 시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다.
- configmap.yaml
```console
apiVersion: v1
kind: ConfigMap
metadata:
  name: ssak3-config
  namespace: ssak3
data:
  api.url.payment: http://payment:8080
```

- reservation.yaml (configmap 사용)
```console
apiVersion: apps/v1
kind: Deployment
metadata:
  name: reservation
  namespace: ssak3
  labels:
    app: reservation
spec:
  replicas: 1
  selector:
    matchLabels:
      app: reservation
  template:
    metadata:
      labels:
        app: reservation
    spec:
      containers:
        - name: reservation
          image: ssak3acr.azurecr.io/reservation:1.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
            - name: api.url.payment
              valueFrom:
                configMapKeyRef:
                  name: ssak3-config
                  key: api.url.payment
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5

---

apiVersion: v1
kind: Service
metadata:
  name: reservation
  namespace: ssak3
  labels:
    app: reservation
spec:
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: reservation
```

- configmap 설정정보 확인
```console
kubectl describe pod/reservation-775fc6574d-kddgd -n ssak3

...중략
Containers:
  reservation:
    Container ID:   docker://af733ea1c805029ad0baf5c448981b3b84def8e4c99656638f2560b48b14816e
    Image:          ssak3acr.azurecr.io/reservation:1.0
    Image ID:       docker-pullable://ssak3acr.azurecr.io/reservation@sha256:5a9eb3e1b40911025672798628d75de0670f927fccefea29688f9627742e3f6d
    Port:           8080/TCP
    Host Port:      0/TCP
    State:          Running
      Started:      Tue, 08 Sep 2020 13:24:05 +0000
    Ready:          True
    Restart Count:  0
    Liveness:       http-get http://:8080/actuator/health delay=120s timeout=2s period=5s #success=1 #failure=5
    Readiness:      http-get http://:8080/actuator/health delay=10s timeout=2s period=5s #success=1 #failure=10
    Environment:
      api.url.payment:  <set to the key 'api.url.payment' of config map 'ssak3-config'>  Optional: false
    Mounts:
      /var/run/secrets/kubernetes.io/serviceaccount from default-token-w4fh5 (ro)
...중략
```


