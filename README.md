 ## AI 코드 리뷰 시스템 및 CI/CD 파이프라인

 ### 프로젝트 소개
 - GitHub PR 생성 시 AI가 자동으로 코드를 리뷰하고 PR 코멘트를 등록하는 시스템
 - 과거 PR 패턴 기반 RAG 검색으로 프로젝트 리뷰
 - 이슈 내용 기반으로 해당 기능의 담당자 조회 및 추천
 - Java 앱 서버와 Python AI 서버로 분리

### 관련 레포지토리
| 레포 | 역할 |
|------|------|
| [AI_CODE_REVIEW_CICD](https://github.com/wooni0714/AI_CODE_REVIEW_CICD) | Java Spring Boot 서버 (웹훅, GitHub API) |
| [AI-ASSISTANT](https://github.com/wooni0714/ai-assistant) | Python FastAPI AI 서버 (RAG, LLM, 에이전트) |


### 개발 환경

**Java 서버**
- Language : Java 17
- Framework : Spring Boot 3.5
- Build : Gradle 8
- AI : Google Gemini API
- CI/CD : GitHub Actions
- Infra : GCP, Docker, Docker Compose
- Webhook : GitHub Webhook

**Python AI 서버**
- Language: Python 3.12
- Framework: FastAPI
- Agent: LangGraph
- AI: Google Gemini API
- Vector DB: ChromaDB
- Cache: Redis
- Infra: GCP, Docker, Docker Compose


### 데이터 시퀀스 다이어그램
<img width="6735" height="7515" alt="Image" src="https://github.com/user-attachments/assets/dcaa7b60-dc14-48d3-bb1c-37cb5d14ce48" />

### 프로젝트 구조

```
src/main/java/wooni/cicd/ai_review/
├── config/
│   ├── ReviewProperties.java       # 환경변수 바인딩
│   └── WebClientConfig.java        # GitHub/Gemini WebClient 설정
│
├── review/
│   ├── dto/
│   │   ├── DiffFile.java           # GitHub PR 변경 파일 정보
│   │   ├── ReviewItem.java         # AI 리뷰 단건 (line, severity, category)
│   │   └── ReviewResult.java       # AI 리뷰 전체 응답
│   ├── service/
│   │   ├── GitHubApiService.java   # GitHub API 호출 (파일 조회, 코멘트 등록)
│   │   ├── AiReviewService.java    ## Python AI 서버 호출하여 AI 코드 리뷰 요청
│   │   └── CodeReviewService.java  # 리뷰 파이프라인
│   └── util/
│       └── ReviewFilter.java       # 리뷰 대상 파일 필터링
│
└── webhook/
    ├── dto/
    │   ├── WebhookPayload.java      # GitHub Webhook 전체 payload
    │   ├── PullRequestEvent.java   # PR 번호, 커밋 SHA
    │   └── RepositoryInfo.java     # 레포지토리 정보
    ├── service/
    │   └── WebhookService.java     # 서명 검증, 이벤트 필터링
    └── controller/
        └── GitHubWebhookController.java  # POST /webhook/github

CI/
└── docker-compose.yml              # 배포용 컨테이너 구성

.github/workflows/
└── deploy.yml                      # GitHub Actions CI/CD
```

### AI 자동 코드 리뷰 결과
<img width="731" height="865" alt="Image" src="https://github.com/user-attachments/assets/e9f3621f-8c27-4259-8661-33030cade268" />

### 기능 담당자 AI 추천 결과
<img width="830" height="131" alt="Image" src="https://github.com/user-attachments/assets/dc907160-c5d4-4179-b383-58445613bb70" />


### API
---
| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/webhook/github` | POST | GitHub Webhook 수신 |
| `/issue/assignee` | POST | 이슈 담당자 추천 |
---
## 클래스별 상세 설명
### Config

#### `ReviewProperties.java`
```java
@ConfigurationProperties(prefix = "review")
public record ReviewProperties(
    String githubToken,
    String webhookSecret,
    String pythonAiUrl,
    int maxRetry,
    long reviewedCommitTtl
)
```
- 환경변수를 불변 객체로 바인딩
- `@ConfigurationProperties` + 생성자 바인딩으로 Setter 미사용
- 모든 서비스에서 주입받아 사용

---

#### `WebClientConfig.java`
- **githubWebClient**: GitHub API 호출용
  - `Authorization: Bearer {token}` 헤더 자동 주입
  - `Accept: application/vnd.github.v3+json` 헤더 설정
- **pythonAiWebClient**: Python AI 서버 호출용
  - `Content-Type: application/json` 헤더 설정
- 공통 타임아웃 설정 (connect 30초, read 60초, write 10초)

---

### Webhook

#### `GitHubWebhookController.java`
- `POST /webhook/github` 엔드포인트
- `@RequestBody`를 `String`으로 받아 서명 검증에 raw body 사용
  - Spring은 RequestBody를 한 번만 읽을 수 있어 String으로 받은 후 직접 파싱
- `pull_request` 이벤트만 처리, 그 외 이벤트는 즉시 200 반환
- 서명 검증 실패 시 401 응답
- 리뷰는 비동기 처리 후 즉시 200 응답 반환

---

#### `WebhookService.java`
- **서명 검증** (HMAC-SHA256)
  - `X-Hub-Signature-256` 헤더와 직접 계산한 해시 비교
  - 불일치 시 `SecurityException` 발생 → 위조 요청 차단
- **이벤트 필터링**
  - `opened`, `synchronize` 이벤트만 리뷰 실행
  - `closed`, `merged` 등 그 외 이벤트 무시
- **비동기 실행**
  - `codeReviewService.review().subscribe()`로 비동기 처리
  - Webhook 응답은 즉시 반환

---

#### `WebhookPayload.java` / `PullRequestEvent.java` / `RepositoryInfo.java`
- GitHub Webhook payload를 Jackson으로 파싱하는 DTO
- `@JsonIgnoreProperties(ignoreUnknown = true)`로 불필요한 필드 무시
- `PullRequestEvent.getHeadSha()`: 최신 커밋 SHA 추출

---

### Review

#### `CodeReviewService.java`
전체 리뷰 파이프라인을 총괄하는 서비스

```
1. Redis에서 중복 리뷰 여부 확인 (커밋 SHA 기준)
2. GitHub API로 PR 변경 파일 조회
3. ReviewFilter로 리뷰 대상 파일 필터링
4. Flux로 파일별 병렬 AI 리뷰 요청
5. 라인 코멘트 등록
6. 요약 코멘트 등록 (ERROR/WARNING/INFO 건수 포함)
7. Redis에 커밋 SHA 저장 (TTL 24시간)
```
- `ReactiveStringRedisTemplate` 사용 (WebFlux 비동기 환경 대응)
- 리뷰 대상 파일 없을 시 AI 요청 없이 스킵

---

#### `GitHubApiService.java`
GitHub API 호출만 담당 (단일 책임 원칙)

| 메서드 | 설명 |
|--------|------|
| `getPrFiles()` | PR 변경 파일 목록 조회 (페이징 처리) |
| `postLineComment()` | Files changed 탭 코드 옆 라인 코멘트 등록 |
| `postSummaryComment()` | Conversation 탭 하단 요약 코멘트 등록 |

- **페이징**: `per_page=100`, `Link` 헤더로 next 페이지 확인, 재귀 호출로 전체 수집
- **URI 처리**: WebClient URI 템플릿의 슬래시(`/`) 인코딩 문제로 문자열 직접 조합
- **라인 코멘트 실패**: 로그만 남기고 스킵 (요약 코멘트에는 영향 없음)

---

#### `AiReviewService.java`
- **githubWebClient**: GitHub API 호출용
  - `Authorization: Bearer {token}` 헤더 자동 주입
  - `Accept: application/vnd.github.v3+json` 헤더 설정
- **pythonAiWebClient**: Python AI 서버 호출용
  - `Content-Type: application/json` 헤더 설정
- 공통 타임아웃 설정 (connect 30초, read 60초, write 10초)

---

#### `ReviewFilter.java`
리뷰 대상 파일 필터링 및 patch 청킹 유틸리티

| 필터 규칙 | 설명 |
|-----------|------|
| Java 파일 아닌 경우 | `.java` 확장자 아니면 제외 |
| 테스트 파일 | `/test/` 경로 제외 |
| 자동생성 파일 | `generated/`, `build/` 경로 제외 |
| QueryDSL Q클래스 | 파일명에 `/Q` 포함 시 제외 |
| 설정 파일 | `Application.java`, `Config.java`, `Properties.java` suffix 제외 |
| patch 없는 파일 | deleted 파일 등 patch null 시 제외 |

- **청킹**: patch 3000자 초과 시 truncate → Gemini API 토큰 한도 초과 방지

---

### DTO

#### `DiffFile.java`
```java
public record DiffFile(String fileName, String patch, String status)
```
- GitHub API 응답에서 파일명, 변경 코드(patch), 상태(added/modified/deleted) 추출

#### `ReviewItem.java`
```java
public record ReviewItem(int line, String severity, String category, String comment, String suggestion, double confidence)
```
- AI 리뷰 단건 결과
- `severity`: ERROR / WARNING / INFO
- `category`: NPE / EXCEPTION / TRANSACTION / NPLUS1 / SECURITY / SOLID / DUPLICATION / OTHER

#### `ReviewResult.java`
```java
public record ReviewResult(
    @JsonProperty("reviews") List reviewItems,
    String summary
)
```
- Gemini API 응답 전체
- `@JsonProperty("reviews")`: Gemini 응답 필드명(`reviews`)과 Java 필드명(`reviewItems`) 매핑

---
