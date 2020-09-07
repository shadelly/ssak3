# Ssak3 - 청소대행 서비스

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
- 결제가 되지 않은 예약건은 아예 거래가 성립되지 않아야 한다  Sync 호출 
### 2. 장애격리
- 통지(알림) 기능이 수행되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다 - Async (event-driven), Eventual Consistency
- 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다  Circuit breaker, fallback
### 3. 성능
- 고객과 청소업체가 자주 예약관리에서 확인할 수 있는 상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다  CQRS
- 상태가 바뀔때마다 카톡 등으로 알림을 줄 수 있어야 한다  Event driven

# 분석/설계

## AS-IS 조직 (Horizontally-Aligned)
  ![image](../docs/images/03.png)

## TO-BE 조직 (Vertically-Aligned)
  ![image](../docs/images/04.png)

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과 : http://www.msaez.io/#/storming/k1eXHY4YSrSFKU3UpQTDRHUvSS23/every/f5d0809e09167fd49a1a95acfc9dd0d2/-MGcF3GTrAc5KsEkYr8b

### 이벤트 도출
  ![image](../docs/images/06.png)
  
### 부적격 이벤트 탈락
  ![image](../docs/images/07.png)

### 액터, 커맨드 부착하여 읽기 좋게
  ![image](../docs/images/08.png)
  
### 어그리게잇으로 묶기
  ![image](../docs/images/09.png)
  
### 바운디드 컨텍스트로 묶기
  ![image](../docs/images/10.png)
  
### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)
  ![image](../docs/images/11.png)
    
### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)
  ![image](../docs/images/12.png)
    
### 기능적 요구사항 검증
  ![기능 요구사항](../docs/images/13.png)
  ![시나리오](../docs/images/14.png)
  ![시나리오](../docs/images/15.png)
  ![시나리오](../docs/images/16.png)
    
  * 호스트가 속소를 등록한다. (ok)
  * 호스트가 숙소를 삭제한다. (ok)
  * 게스트가 숙소를 선택하여 사용 예약한다. (ok)
  * 게스트가 결제한다. (ok)
  * 결제가 완료되면, 결제 & 예약 내용을 게스트에게 전달한다. (ok)
  * 예약 내역을 호스트에게 전달한다. (ok)
  * 게스트는 본인의 예약 내용 및 상태를 조회한다. (ok)
  * 게스트는 본인의 예약을 취소할 수 있다. (ok)
  * 예약이 취소되면, 결제를 취소한다. (ok)
  * 결제가 취소되면, 결제 취소 내용을 게스트에게 전달한다. (ok) 

### 비기능 요구사항 검증
  ![시나리오](../docs/images/17.png)

## 헥사고날 아키텍처 다이어그램 도출 
  ![시나리오](../docs/images/18.png)
