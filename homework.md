#1.SAGA
#2.CQRS
```java
package CleaningServicePark;

import CleaningServicePark.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class DashBoardViewViewHandler {


    @Autowired
    private DashBoardViewRepository dashBoardViewRepository;
//...중략

@StreamListener(KafkaProcessor.INPUT)
    public void whenKindRegistered_then_UPDATE_4(@Payload KindRegistered kindRegistered) {
        try {
            if (kindRegistered.isMe()) {
                // view 객체 조회
                List<DashBoardView> dashBoardViewList = dashBoardViewRepository.findByRequestId(kindRegistered.getRequestId());
                for(DashBoardView dashBoardView : dashBoardViewList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    dashBoardView.setPayKind(kindRegistered.getKind());
                    // view 레파지 토리에 save
                    dashBoardViewRepository.save(dashBoardView);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenKindChanged_then_UPDATE_5(@Payload KindChanged kindChanged) {
        try {
            if (kindChanged.isMe()) {
                // view 객체 조회
                List<DashBoardView> dashBoardViewList = dashBoardViewRepository.findByRequestId(kindChanged.getRequestId());
                for(DashBoardView dashBoardView : dashBoardViewList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    dashBoardView.setPayKind(kindChanged.getKind());
                    // view 레파지 토리에 save
                    dashBoardViewRepository.save(dashBoardView);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
//...중략
}
```
#3.CORERELATION
#4.REQ/RESP
분석단계에서의 조건 중 하나로 예약->결제 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제 서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
```java
@FeignClient(name="Payment", url="${api.url.payment}")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void payRequest(@RequestBody Payment payment);

}
```
- 결제수단 등록을 한 직후(@PostPersist) 결제가 완료되도록 처리
```java
@Entity
@Table(name="Paymethod_table")
public class Paymethod {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String kind;
    private Long number;
    private Long requestId;

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
}
```

- 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 결제시스템 등록도 되지않음을 확인
```
# 결제 서비스를 잠시 내려놓음
$ kubectl delete -f payment.yaml

NAME                           READY   STATUS    RESTARTS   AGE
cleaning-bf474f568-vxl8r       2/2     Running   0          137m
dashboard-7f7768bb5-7l8wr      2/2     Running   0          136m
gateway-6dfcbbc84f-rwnsh       2/2     Running   0          37m
message-69597f6864-mhwx7       2/2     Running   0          137m
paymethod-646dcb9ffb-llrzk     2/2     Running   0          12m
reservation-775fc6574d-kddgd   2/2     Running   0          144m
siege                          2/2     Running   0          3h39m

# 결제수단등록 (siege 에서)
http POST http://paymethod:8080/paymethods kind=credit number=40095003 requestId=1 payKindRegStatus=PaymentKindRegistered #Fail

# 결제수단등록 시 에러 내용
HTTP/1.1 500 Internal Server Error
content-type: application/json;charset=UTF-8
date: Wed, 09 Sep 2020 15:52:52 GMT
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 82

{
    "error": "Internal Server Error",
    "message": "Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction",
    "path": "/paymethods",
    "status": 500,
    "timestamp": "2020-09-09T15:52:53.135+0000"
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


# 결제수단등록 (siege 에서)
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
#5.GATEWAY
- gateway service type 변경 (ClusterIP -> LoadBalancer)
```console
$ kubectl edit service/gateway -n ssak5

root@ssak5-vm:~/ssak5# kubectl get service -n ssak5
NAME          TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)          AGE
cleaning      ClusterIP      10.0.55.227    <none>         8080/TCP         30m
dashboard     ClusterIP      10.0.108.26    <none>         8080/TCP         30m
gateway       LoadBalancer   10.0.55.192    20.39.188.50   8080:32750/TCP   31m
message       ClusterIP      10.0.23.249    <none>         8080/TCP         30m
payment       ClusterIP      10.0.213.242   <none>         8080/TCP         30m
paymethod     ClusterIP      10.0.234.6     <none>         8080/TCP         30m
reservation   ClusterIP      10.0.126.188   <none>         8080/TCP         30m
```
#6.DEPLOY/PIPELINE
#7.CIRCUIT BREAKER
#8.AUTOSCALE(HPA)
#9.ZERO-DOWNTIME DEPLOY
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함 (위의 시나리오에서 제거되었음)
```console
kubectl delete horizontalpodautoscaler.autoscaling/paymethod -n ssak5
```
#10.CONFIGMAP / PERSISTENCE VOLUME
- 시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리
- configmap.yaml
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ssak5-config
  namespace: ssak5
data:
  api.url.payment: http://payment:8080
```

- paymethod.yaml (configmap 사용)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: paymethod
  namespace: ssak5
  labels:
    app: paymethod
spec:
  replicas: 1
  selector:
    matchLabels:
      app: paymethod
  template:
    metadata:
      labels:
        app: paymethod
    spec:
      containers:
        - name: paymethod
          image: ssak5acr.azurecr.io/paymethod:1.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
            - name: api.url.payment
              valueFrom:
                configMapKeyRef:
                  name: ssak5-config
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
  name: paymethod
  namespace: ssak5
  labels:
    app: paymethod
spec:
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: paymethod
```
- configmap 설정 정보 확인
```console
kubectl describe pod/paymethod-775fc6574d-kddgd -n ssak5

...중략
Containers:
  paymethod:
    Container ID:   docker://af733ea1c805029ad0baf5c448981b3b84def8e4c99656638f2560b48b14816e
    Image:          ssak5acr.azurecr.io/reservation:1.0
    Image ID:       docker-pullable://ssak5acr.azurecr.io/paymethod@sha256:5a9eb3e1b40911025672798628d75de0670f927fccefea29688f9627742e3f6d
    Port:           8080/TCP
    Host Port:      0/TCP
    State:          Running
      Started:      Tue, 08 Sep 2020 13:24:05 +0000
    Ready:          True
    Restart Count:  0
    Liveness:       http-get http://:8080/actuator/health delay=120s timeout=2s period=5s #success=1 #failure=5
    Readiness:      http-get http://:8080/actuator/health delay=10s timeout=2s period=5s #success=1 #failure=10
    Environment:
      api.url.payment:  <set to the key 'api.url.payment' of config map 'ssak5-config'>  Optional: false
    Mounts:
      /var/run/secrets/kubernetes.io/serviceaccount from default-token-w4fh5 (ro)
...중략
```
#11.POLYGLOT
#12.SELF-HEALING(LIVENESS PROBE)
* Paymethod\kubernetes\deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: Paymethod
  labels:
    app: Paymethod
spec:
  replicas: 1
  selector:
    matchLabels:
      app: Paymethod
  template:
    metadata:
      labels:
        app: Paymethod
    spec:
      containers:
        - name: Paymethod
          image: username/Paymethod:latest
          ports:
            - containerPort: 8080
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
```
