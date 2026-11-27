# 배포 (Docker Compose + Nginx)

ARM(aarch64) 리눅스 서버 한 대에 다른 서비스와 함께 올리는 기준이다. 서버에서는 빌드하지 않고, 로컬에서 만든 이미지를 받아 실행만 한다.

| 컨테이너 | 이미지 | 바깥에 여는 포트 | 메모리 상한 |
|---|---|---|---|
| `rd_app` | `vcg258/renova-delve` | `127.0.0.1:8083` (Nginx만 접근) | 1GB (힙은 75%) |
| `rd_db` | `pgvector/pgvector:pg16` | 없음 | 512MB |
| `rd_redis` | `redis:7-alpine` (AOF) | 없음 | 128MB |

DB와 Redis는 compose 내부 네트워크에서만 보인다. 스키마는 첫 기동 때 Flyway가 만들고, VectorStore 테이블과 임베딩은 Spring AI와 기동 시 ETL이 채운다.

## 1. 이미지 만들기 (로컬)

화면에 박히는 값(`VITE_`)은 빌드할 때 정해진다. 운영자 이름과 문의처를 넣고 jar를 만든다.

```powershell
$env:VITE_OPERATOR_NAME = "운영자 이름"
$env:VITE_OPERATOR_CONTACT = "문의 이메일"
./gradlew bootJar -PwithFrontend
```

ARM 서버용 이미지를 만들어 올린다. `VERSION`은 `build.gradle`의 version과 같아야 한다.

```powershell
docker buildx build --platform linux/arm64 --build-arg VERSION=0.11.0 -t vcg258/renova-delve:0.11.0 -t vcg258/renova-delve:latest --push .
```

## 2. 서버 준비 (처음 한 번)

`deploy/docker-compose.yml`과 `deploy/.env.example`을 서버의 `~/renova`로 복사하고, `.env.example`을 `.env`로 바꿔 값을 채운다.

```powershell
scp deploy/docker-compose.yml deploy/.env.example ubuntu@서버:~/renova/
```

- `DB_PASSWORD`, `JWT_SECRET_KEY`, `ADMIN_PASSWORD`는 서로 다른 긴 임의 문자열로 채운다(`openssl rand -base64 48`).
- `APP_BASE_URL`은 실제 접속 주소(https)다. 비밀번호 재설정 메일의 링크가 이 주소로 만들어진다.

메모리가 바닥났을 때 다른 서비스까지 강제 종료되지 않도록 스왑을 둔다.

```powershell
sudo fallocate -l 2G /swapfile; sudo chmod 600 /swapfile; sudo mkswap /swapfile; sudo swapon /swapfile; echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 3. 실행

```powershell
cd ~/renova; docker compose pull; docker compose up -d
```

기동 확인: `docker logs -f rd_app`에서 `Started RenovaNDelveApplication`과 `[GameDataETL] RDB 동기화 완료`가 보이면 된다. 첫 기동의 ETL은 게임 데이터 전체를 임베딩하므로 OpenAI 임베딩 호출이 한 번 일어난다(2회차부터는 바뀐 행만).

관리자 계정은 `.env`의 `ADMIN_LOGIN_ID` / `ADMIN_PASSWORD`로 만들어진다.

## 4. Nginx와 HTTPS

운영 프로파일의 로그인 유지 쿠키는 HTTPS 전용(`Secure`)이라 HTTPS 없이는 새로고침마다 로그아웃된다.

`/etc/nginx/sites-available/renova`:

```nginx
server {
    server_name 도메인;
    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8083;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # AI 노드 생성은 최대 75초 걸린다 (Nginx 기본값 60초로는 끊긴다)
        proxy_read_timeout 120s;
    }

    listen 80;
}
```

```powershell
sudo ln -s /etc/nginx/sites-available/renova /etc/nginx/sites-enabled/; sudo nginx -t; sudo systemctl reload nginx; sudo certbot --nginx -d 도메인
```

`X-Forwarded-For`·`X-Forwarded-Proto`는 빠뜨리면 안 된다. 서버가 이 헤더로 실제 접속 IP(요청 제한)와 HTTPS 여부를 판단한다.

## 5. 업데이트

1의 순서로 새 이미지를 올린 뒤 서버에서:

```powershell
cd ~/renova; docker compose pull app; docker compose up -d app
```

엔티티가 바뀐 버전이면 Flyway가 기동 때 새 마이그레이션을 적용한다. DB 볼륨(`rd_pgdata`)은 그대로 유지된다.

## 백업

```powershell
docker exec rd_db pg_dump -U renova renova_n_delve | gzip > renova-$(date +%F).sql.gz
```
