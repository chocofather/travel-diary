# TripBora 운영 구조

TripBora 운영 환경의 **현재 확정된 구조**를 기록한다.
배포, Docker, Cloudflare, 운영 DB, DBeaver 관련 작업을 하는 AI(Claude·Codex·ChatGPT)와 사람은 이 문서를 먼저 확인한다.
운영환경을 추정하지 않는다.

> 비밀번호·API Key·토큰·Cloudflare secret 같은 실제 값은 이 문서에 적지 않는다. 파일 위치와 변수 이름만 기록한다.

---

## 1. 운영 호스트

- 별도의 AWS·VPS·클라우드 VM에서 실행하는 구조가 **아니다.**
- 운영 애플리케이션과 운영 MySQL은 사용자의 **MacBook Air에서 Docker Desktop으로** 실행된다.
  - 운영 호스트: `gimminjun-ui-MacBookAir.local`
- 즉 MacBook Air가 실질적인 운영 서버다.
  - Docker Desktop을 완전히 종료하면 운영 app과 운영 MySQL 컨테이너도 멈춘다.

---

## 2. 운영 Docker

Compose project: `tripbora-prod` (저장소의 `compose.prod.yaml`)

| | 운영 App | 운영 MySQL |
|---|---|---|
| container_name | `tripbora-prod-app` | `tripbora-prod-mysql` |
| image | `tripbora-prod-app:local` | `mysql:9.2.0` |
| host binding | `127.0.0.1:8080` → container `8080` | `127.0.0.1:3307` → container `3306` |
| 기타 | Spring profile `prod` | database `tripbora_prod`, 앱 DB 사용자 `tripbora_app`, volume `tripbora_prod_mysql92_data` |

- 운영 MySQL은 외부 인터넷에 직접 공개하지 않는다. `127.0.0.1`에만 바인딩되어 있다.

---

## 3. Cloudflare

| 항목 | 값 |
|---|---|
| Tunnel name | `tripbora-prod` |
| Tunnel connector | `gimminjun-ui-MacBookAir.local` |
| Published application hostname | `tripbora.com` |
| Service | `http://127.0.0.1:8080` |

요청 흐름:

```
사용자
→ tripbora.com
→ Cloudflare
→ Cloudflare Tunnel (tripbora-prod)
→ MacBook Air
→ 127.0.0.1:8080
→ tripbora-prod-app
```

Cloudflare는 애플리케이션이나 MySQL을 호스팅하지 않는다. 외부 HTTPS·도메인 요청을 MacBook의 로컬 운영 앱으로 전달하는 통로다.

---

## 4. 운영 DB 연결 (DBeaver)

DBeaver는 운영 호스트와 같은 MacBook에서 실행하므로 **SSH Tunnel이 필요 없다.**

| 항목 | 값 |
|---|---|
| Host | `127.0.0.1` |
| Port | `3307` |
| Database | `tripbora_prod` |
| Username | `tripbora_app` |
| Password | `/Users/minjun/tripbora-config/docker-prod.env`의 `DB_PASSWORD` |

- Docker Desktop이 꺼져 있으면 3307 포트가 없으므로 `Connection refused`가 나는 것이 정상이다.

### DBeaver 권장 연결 이름

운영과 개발을 이름과 포트로 바로 구분할 수 있게 연결을 둘로 나눈다.

| 연결 이름 | Host | Port | Database |
|---|---|---|---|
| `TripBora - DEV` | `127.0.0.1` | `3308` | 개발 env 파일의 `DB_NAME` (`.env.docker.local`, 현재 `mydb`) |
| `TripBora - PRODUCTION` | `127.0.0.1` | `3307` | `tripbora_prod` |

- PRODUCTION 연결은 DBeaver의 연결 유형을 Production으로 지정해 색으로도 구분하는 것을 권장한다.

---

## 5. 운영 환경파일

- 운영 app env file: `/Users/minjun/tripbora-config/docker-prod.env`
- 이 파일은 저장소 밖에 있고, 실제 값은 이 문서나 Git에 기록하지 않는다.

Compose에서 쓰는 주요 변수(이름만):

- `DB_USERNAME`
- `DB_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `UPLOAD_PATH`
- `DIARY_PRIVATE_PATH`
- `COVER_LIBRARY_PRIVATE_PATH`

`UPLOAD_PATH`, `DIARY_PRIVATE_PATH`, `COVER_LIBRARY_PRIVATE_PATH`는 호스트 경로다. 컨테이너 안의 `/data/uploads`, `/data/diary-private`, `/data/cover-library-private`에 bind mount된다(`compose.prod.yaml`).

---

## 6. 운영 / 개발 구분

운영 환경의 식별값:

| 항목 | 값 |
|---|---|
| 컨테이너 | `tripbora-prod-app`, `tripbora-prod-mysql` |
| DB | `tripbora_prod` |
| 포트 | 8080(app), 3307(mysql) |
| 도메인 | `tripbora.com`이 이 환경을 바라본다 |

저장소의 개발용 `compose.yaml`(project `tripbora-dev`)도 있다.

| 항목 | 값 |
|---|---|
| 컨테이너 | `tripbora-dev-app`, `tripbora-dev-mysql` |
| app 포트 | 8081 |
| DB 이름 | env 파일(`.env.docker.local`)의 `DB_NAME` |
| volume | `tripbora_dev_mysql92_data` |

MySQL 호스트 포트는 운영과 개발이 다르다. 둘 다 `127.0.0.1`에만 바인딩한다.

| 환경 | 호스트 → 컨테이너 | DB |
|---|---|---|
| DEV (`tripbora-dev-mysql`) | `127.0.0.1:3308` → MySQL `3306` | `DB_NAME` (현재 `mydb`) |
| PROD (`tripbora-prod-mysql`) | `127.0.0.1:3307` → MySQL `3306` | `tripbora_prod` |

- 호스트(DBeaver, IntelliJ에서 띄운 앱 등)에서 접속할 때만 3308/3307을 쓴다.
- Docker 네트워크 안의 앱 컨테이너는 두 환경 모두 `mysql:3306`으로 접속한다.
- 포트가 달라 두 MySQL을 동시에 띄울 수 있다. 그래도 DB 작업 전에는 접속한 DB가 의도한 쪽(`tripbora_prod`인지 아닌지)인지 확인한다.

규칙:

- 운영·개발을 설명하거나 작업할 때는 항상 **컨테이너명, DB명, 포트를 확인한 뒤** 답한다.
- `prod`라는 이름만 보고 별도 클라우드 서버가 있다고 추정하지 않는다.
- `.claude/skills/deploy`는 과거 launchd 방식이라 현재 구조와 맞지 않는다(CLAUDE.md 참고). 배포 절차의 근거로 쓰지 않는다.

---

## 7. 운영 상태 확인 명령

```bash
docker info                                   # Docker Desktop 동작 여부
docker ps --filter name=tripbora-prod         # 운영 컨테이너
docker ps --filter name=tripbora-prod-mysql   # 운영 MySQL
docker port tripbora-prod-mysql               # 예상: 3306/tcp -> 127.0.0.1:3307
docker port tripbora-prod-app                 # 예상: 8080/tcp -> 127.0.0.1:8080
docker port tripbora-dev-mysql                # (개발) 예상: 3306/tcp -> 127.0.0.1:3308
```

---

## 8. 운영 원칙

- Docker Desktop을 종료하면 TripBora 운영 서비스도 멈춘다.
- MacBook이 꺼지거나 절전 등으로 서비스가 멈추면 `tripbora.com`도 정상 서비스되지 않는다.
- Cloudflare Tunnel이 살아 있어도 원본 서비스 `127.0.0.1:8080`이 죽어 있으면 사이트는 동작하지 않는다.
- 운영 DB를 다루기 전 반드시 `tripbora_prod`인지 확인한다. 운영 DB와 개발 DB를 혼동하지 않는다.
- 운영 DB에 SQL을 자동 실행하지 않는다. DB 변경이 필요하면 프로젝트 규칙대로 SQL만 제안하고 사용자가 직접 실행한다(CLAUDE.md의 DB 원칙, `db-migration` skill).

---

## 9. 향후 서버 이전

- 지금은 MacBook Air가 운영 호스트다.
- AWS·VPS·클라우드 VM 등으로 이전하면 **이 문서를 먼저 갱신한다.**
- 갱신할 때는 아래 항목을 새 구조로 **교체**한다. 기존 Mac 운영 구조를 사실처럼 남겨 두지 않는다.
  - 실제 호스트
  - Docker 위치
  - Cloudflare Tunnel connector
  - DB 접근 방식
  - DBeaver 접근 방식
