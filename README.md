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


# sample
```console
```