## MSAEZ 소스 다운로드

## Git 올리기

## 서버에서 Build

## Kubenetis install
```console
sudo apt-get update && sudo apt-get install -y apt-transport-https
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee -a /etc/apt/sources.list.d/kubernetes.list
sudo apt-get update
sudo apt-get install -y kubectl
```
## Azure-Cli  install
```console
# curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash
# az login -u dragon.skcc@gmail.com -p skccadmin!1
```

## Azure Configure
```console
- Azure (http://portal.azure.com) : dragon.skcc@gmail.com
- AZure 포탈에서 리소스 그룹 > 쿠버네티스 서비스 생성 > 컨테이너 레지스트리 생성
- 리소스 그룹 생성 : dragon-rg
- 컨테이너 생성( Kubernetes ) : dragonAKS
- 레지스트리 생성 : dragonacr, dragonacr.azurecr.io
```

## Azure 인증
```console
# az account set --subscription "XXX"
# az aks get-credentials --resource-group ssak3-rg --name dragon.skcc@gmail.com
# az acr login --name dragon.skcc@gmail.com

```

## Azure AKS와 ACR 연결
```console
az aks update -n admin4-aks -g admin4-resourcegroup --attach-acr admin4
```

## Kafka install (kubernetes/helm)
참고 - (https://workflowy.com/s/msa/27a0ioMCzlpV04Ib#/a7018fb8c62)
```console

curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get | bash
kubectl --namespace kube-system create sa tiller      
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller
helm init --service-account tiller
kubectl patch deploy --namespace kube-system tiller-deploy -p '{"spec":{"template":{"spec":{"serviceAccount":"tiller"}}}}'

helm repo add incubator http://storage.googleapis.com/kubernetes-charts-incubator
helm repo update

helm install --name my-kafka --namespace kafka incubator/kafka
```

## Kafka delete
```console
helm del my-kafka  --purge
```

## Lab. Istio 설치
```console
curl -L https://git.io/getLatestIstio | ISTIO_VERSION=1.4.5 sh -
cd istio-1.4.5
export PATH=$PWD/bin:$PATH
for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
kubectl apply -f install/kubernetes/istio-demo.yaml
kubectl get pod -n istio-system
```

## kiali 설치
- 에러발생, 위의 istio 설치에서 완료됨
```console

vi kiali.yaml    

apiVersion: v1
kind: Secret
metadata:
  name: kiali
  namespace: istio-system
  labels:
    app: kiali
type: Opaque
data:
  username: YWRtaW4=
  passphrase: YWRtaW4=

----- save (:wq)

kubectl apply -f kiali.yaml
helm template --set kiali.enabled=true install/kubernetes/helm/istio --name istio --namespace istio-system > kiali_istio.yaml    
kubectl apply -f kiali_istio.yaml
```
- load balancer로 변경
```console
kubectl edit service/kiali -n istio-system
(ClusterIP -> LoadBalancer)
```

## namespace create
```console
kubectl create namespace mybnb
```

- istio enabled
```console
kubectl label namespace mybnb istio-injection=enabled
```

## siege deploy
```console
cd mybnb/yaml
kubectl apply -f siege.yaml 
kubectl exec -it siege -n mybnb -- /bin/bash
apt-get update
apt-get install httpie
```

## image build & push (이미지명 정해지면 수정)
- compile
```console
cd ssak3/gateway
mvn package
```

- for azure cli
```console
# docker build -t dragon.skcc@gmail.com/my-nginx:v2   - docker build
# docker images    : docker images 확인
# docker push dragon.skcc@gmail.com/my-nginx:v2   : docker를 리파지토리에 push
```
- for dockerhub
```console
docker build -t jihwancha/mybnb-html:latest .
docker push jihwancha/mybnb-html:latest
```
## application deploy
```console
# kubectl create deploy my-nginx --image=dragon.skcc@gmail.com/my-nginx:v2 -  AZure portal 에서 확인
# kubectl create -f nginx.yaml
# kubectl apply -f nginx.yaml
# kubectl 동사 목적어
# kubectl get nodes   - 클러스터에 만들어 진 노드의 수 (worker nodes. VM. 서버)
# kubectl get pod
# kubectl get pod -o wide  - Output을 좀더 많이 출력. node 정보도 같이 보여짐.

-- <변경 필요>
kubectl create ns cna-shop
kubectl label ns cna-shop istio-injection=enabled
kubectl create deploy order --image=admin4.azurecr.io/cna-order:v1 -n cna-shop
kubectl create deploy delivery --image=admin4.azurecr.io/cna-delivery:v1 -n cna-shop
kubectl create deploy customercenter --image=admin4.azurecr.io/cna-customercenter:v1 -n cna-shop
kubectl create deploy gateway --image=admin4.azurecr.io/cna-gateway:v1 -n cna-shop

kubectl expose deploy order --port=8080 -n cna-shop
kubectl expose deploy delivery --port=8080 -n cna-shop
kubectl expose deploy customercenter --port=8080 -n cna-shop
kubectl expose deploy gateway --port=8080 -n cna-shop
-- <여기까지>

cd mybnb/yaml

kubectl apply -f configmap.yaml

kubectl apply -f gateway.yaml
kubectl apply -f html.yaml
kubectl apply -f room.yaml
kubectl apply -f booking.yaml
kubectl apply -f pay.yaml
kubectl apply -f mypage.yaml

kubectl apply -f alarm.yaml
kubectl apply -f review.yaml

```

## 숙소 등록 (siege 에서)
```console
http POST http://room:8080/rooms name=호텔 price=1000 address=서울 host=Superman
http POST http://room:8080/rooms name=펜션 price=1000 address=양평 host=Superman
http POST http://room:8080/rooms name=민박 price=1000 address=강릉 host=Superman
```
## 예약 (siege 에서)
```console
http POST http://booking:8080/bookings roomId=1 name=호텔 price=1000 address=서울 host=Superman guest=배트맨 usedate=20201010
```
##예약 부하 발생 (siege 에서)
```console
siege -v -c100 -t60S -r10 --content-type "application/json" 'http://booking:8080/bookings POST {"roomId":1, "name":"호텔", "price":1000, "address":"서울", "host":"Superman", "guest":"배트맨", "usedate":"20201230"}'
kiali 확인
접속정보 : ip
http://external-ip:20001 
(admin/admin)
```
## auto scale out settings
```console
kubectl autoscale deploy booking -n mybnb --min=1 --max=10 --cpu-percent=15
kubectl get deploy booking -n mybnb -w
```
## auto scale out delete
```console
kubectl delete hpa booking -n mybnb
```
## 부하 발생 (siege 에서)
```console
siege -v -c100 -t60S -r10 --content-type "application/json" 'http://booking:8080/bookings'
```