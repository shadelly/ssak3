# Azure Cloud Final. Ssak3

# Azure 환경

- Azure (http://portal.azure.com) : dragon.skcc@gmail.com
- AZure 포탈에서 리소스 그룹 > 쿠버네티스 서비스 생성 > 컨테이너 레지스트리 생성
- 리소스 그룹 생성 : ssak3-rg
- 컨테이너 생성( Kubernetes ) : ssak3-k8s
- 레지스트리 생성 : ssak3acr, ssak3acr.azurecr.io

# 구현

## azure 로그인
```console
# az account set --subscription "XXX"
# az aks get-credentials --resource-group ssak3-rg --name dragon.skcc@gmail.com
# az acr login --name dragon.skcc@gmail.com
```

## Docker파일 생성
```console
# nano Dockerfile - Dockerfile 생성
# docker build -t dragon.skcc@gmail.com/my-nginx:v2   - docker build
# docker images    : docker images 확인
# docker push dragon.skcc@gmail.com/my-nginx:v2   : docker를 리파지토리에 push

# kubectl create deploy my-nginx --image=dragon.skcc@gmail.com/my-nginx:v2 -  AZure portal 에서 확인
# kubectl create -f nginx.yaml
# kubectl apply -f nginx.yaml
# kubectl 동사 목적어
# kubectl get nodes   - 클러스터에 만들어 진 노드의 수 (worker nodes. VM. 서버)
# kubectl get pod
# kubectl get pod -o wide  - Output을 좀더 많이 출력. node 정보도 같이 보여짐.
```

## azure 클라이언트 설치하기
```console
# curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash
# az login -u dragon.skcc@gmail.com -p skccadmin!1
```

## Local에 AKS(Azure Kubernetes Service) 클러스터 접속정보 설정
```console
# az aks get-credentials --resource-group ssak3-rg --name ssak3-k8s
```

## Local에 AKS(Azure Kubernetes Service) 클러스터 접속정보 설정
```console
# az aks get-credentials --resource-group ssak3-rg --name ssak3-k8s
```

## Kubectl install
```console
# sudo apt-get update && sudo apt-get install -y apt-transport-https
# curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
# echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee -a /etc/apt/sources.list.d/kubernetes.list
# sudo apt-get update
# sudo apt-get install -y kubectl
```

## Docker파일 생성
```console
# nano Dockerfile - Dockerfile 생성
# docker build -t dragon.skcc@gmail.com/my-nginx:v2   - docker build
# docker images    : docker images 확인
# docker push dragon.skcc@gmail.com/my-nginx:v2   : docker를 리파지토리에 push

# kubectl create deploy my-nginx --image=dragon.skcc@gmail.com/my-nginx:v2 -  AZure portal 에서 확인
# kubectl create -f nginx.yaml
# kubectl apply -f nginx.yaml
# kubectl 동사 목적어
# kubectl get nodes   - 클러스터에 만들어 진 노드의 수 (worker nodes. VM. 서버)
# kubectl get pod
# kubectl get pod -o wide  - Output을 좀더 많이 출력. node 정보도 같이 보여짐.
```

## ReplicaSets
```console
kubectl scale type/type명 --replicas=개수
kubectl scale replicaset/frontend --replicas=5 : replica 개수 조정
kubectl scale deployment/my-nginx --replicas=3
```

## Kafka install (kubernetes/helm)
```console
curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get | bash
kubectl --namespace kube-system create sa tiller      
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller
helm init --service-account tiller
kubectl patch deploy --namespace kube-system tiller-deploy -p '{"spec":{"template":{"spec":{"serviceAccount":"tiller"}}}}'

helm repo add incubator http://storage.googleapis.com/kubernetes-charts-incubator
helm repo update

helm install --name my-kafka --namespace kafka incubator/kafka
Kafka delete
helm del my-kafka  --purge
Lab. Istio 설치
curl -L https://git.io/getLatestIstio | ISTIO_VERSION=1.4.5 sh -
cd istio-1.4.5
export PATH=$PWD/bin:$PATH
for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
kubectl apply -f install/kubernetes/istio-demo.yaml

kubectl get pod -n istio-system
```

## kiali 설치

# sample
```console
```