## zoom 회의 ID 
833 1429 7004


## Git
```console
# Github에 있는 소스를 다운 받으면 ssk3 폴더가 생성됨
git clone https://github.com/dragon-skcc/ssak3.git
# 주요 사용 명령어
git status
```

## ssak3 external IP
- gateway 52.141.27.193:8080
- kiali 20.41.120.4:20001

## Jupyter
```console
https://20.194.23.7:8888
skccadmin
```

## Kubectl install
```
sudo apt-get update && sudo apt-get install -y apt-transport-https
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee -a /etc/apt/sources.list.d/kubernetes.list
sudo apt-get update
sudo apt-get install -y kubectl
```

## Azure-Cli  install
```console
# curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash
# az login -u  -p
```

## Azure Configure
```console
- Azure (http://portal.azure.com) : TeamA@gkn2019hotmail.onmicrosoft.com
- AZure 포탈에서 리소스 그룹 > 쿠버네티스 서비스 생성 > 컨테이너 레지스트리 생성
- 리소스 그룹 생성 : ssak3-rg
- 컨테이너 생성( Kubernetes ) : ssak3-aks
- 레지스트리 생성 : ssak3acr, ssak3acr.azurecr.io
- azure container repository 이름 : cleaning
- container registry image : ssak3acr.azurecr.io/reservation, payment....
```

## Azure 인증
```console
# az login
# az aks get-credentials --resource-group ssak3-rg --name ssak3-aks
# az acr login --name ssak3acr --expose-token

```

## Azure AKS와 ACR 연결
```console
az aks update -n ssak3-aks -g ssak3-rg --attach-acr ssak3acr
```

## kubectl로 확인
```console
kubectl config current-context
kubectl get all
```

## jdk설치
```console
sudo apt-get update
sudo apt install default-jdk
[bash에 환경변수 추가]
1. cd ~
2. nano .bashrc 
3. 편집기 맨 아래로 이동
4. (JAVA_HOME 설정 및 실행 Path 추가)
export JAVA_HOME=‘/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$PATH:$JAVA_HOME/bin:.

ctrl + x, y 입력, 종료
source ~/.bashrc
5. 설치확인
echo $JAVA_HOME
java -version

```

## 리눅스에 Docker client 설치
```console
sudo apt-get update
sudo apt install apt-transport-https ca-certificates curl software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add 
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
sudo apt update
sudo apt install docker-ce
# 리눅스 설치시 생성한 사용자 명 입력
sudo usermod -aG docker skccadmin
```

## 리눅스에 docker demon install
```console
sudo apt-get update
sudo apt-get install \
     apt-transport-https \
     ca-certificates \
     curl \
     gnupg-agent \
     software-properties-common

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo apt-key fingerprint 0EBFCD88

sudo add-apt-repository \
     "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
     $(lsb_release -cs) \
     stable"

sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io


(demon server 시작)
sudo service docker start
(확인)
docker version
sudo docker run hello-world

```

## docker hub 계정로그인
ID : dragonskcc

## Docker demon과 Docker client 연결
```console
cd
nano .bashrc
맨아래 줄에 아래 환경변수 추가
방향키로 맨 아래까지 내린 다음, 새로운 행에 아래 내용 입력
export DOCKER_HOST=tcp://0.0.0.0:2375 
저장 & 종료 : Ctrl + x, 입력 후, y 입력  후 엔터
source ~/.bashrc
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


## Istio 설치
```console
kubectl create namespace istio-system

curl -L https://git.io/getLatestIstio | ISTIO_VERSION=1.4.5 sh -
cd istio-1.4.5
export PATH=$PWD/bin:$PATH
for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
kubectl apply -f install/kubernetes/istio-demo.yaml
kubectl get pod -n istio-system
```

## kiali 설치
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
kubectl create namespace ssak3
```
## namespace 선택 설정 (-n ssak3 옵션을 주지 않도록 default 작업 ns 설정 방법)
```console
kubectl config set-context --current --namespace=ssak3
```

- istio enabled
```console
kubectl label namespace ssak3 istio-injection=enabled
```

## siege deploy
```console
cd ssak3/yaml
kubectl apply -f siege.yaml 
kubectl exec -it siege -n ssak3 -- /bin/bash
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
docker build -t ssak3acr.azurecr.io/gateway .
docker images
docker push ssak3acr.azurecr.io/gateway
```

## application deploy
```console
kubectl create deploy gateway --image=ssak3acr.azurecr.io/gateway -  AZure portal 에서 확인
kubectl create -f gateway.yaml
kubectl apply -f gateway.yaml
# kubectl 동사 목적어
# kubectl get nodes   - 클러스터에 만들어 진 노드의 수 (worker nodes. VM. 서버)
# kubectl get pod
# kubectl get pod -o wide  - Output을 좀더 많이 출력. node 정보도 같이 보여짐.

kubectl create ns ssak3
kubectl label ns ssak3 istio-injection=enabled
kubectl create deploy gateway --image=ssak3acr.azurecr.io/gateway -n ssak3
kubectl create deploy reservation --image=ssak3acr.azurecr.io/reservation -n ssak3
kubectl create deploy cleaning --image=ssak3acr.azurecr.io/cleaning -n ssak3
kubectl create deploy dashboard --image=ssak3acr.azurecr.io/dashboard -n ssak3
kubectl create deploy message --image=ssak3acr.azurecr.io/message -n ssak3
kubectl create deploy payment --image=ssak3acr.azurecr.io/payment -n ssak3

kubectl expose deploy gateway --port=8080 -n ssak3
kubectl expose deploy reservation --port=8080 -n ssak3
kubectl expose deploy cleaning --port=8080 -n ssak3
kubectl expose deploy dashboard --port=8080 -n ssak3
kubectl expose deploy message --port=8080 -n ssak3
kubectl expose deploy payment --port=8080 -n ssak3

cd ssak3/yaml

kubectl apply -f configmap.yaml
kubectl apply -f gateway.yaml
kubectl apply -f cleaning.yaml
kubectl apply -f reservation.yaml
kubectl apply -f payment.yaml
kubectl apply -f dashboard.yaml
kubectl apply -f message.yaml
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
kubectl autoscale deploy booking -n clean --min=1 --max=10 --cpu-percent=15
kubectl get deploy booking -n clean -w
```
## auto scale out delete
```console
kubectl delete hpa booking -n clean
```
## 부하 발생 (siege 에서)
```console
siege -v -c100 -t60S -r10 --content-type "application/json" 'http://booking:8080/bookings'
```