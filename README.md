# 벼리 · Byeori

> **Byeori — The Thread of K-Tradition**
> 흩어져 있는 한국 전통 테마 관광 정보를 하나로 엮어, 내·외국인에게 명확한 관광 동선을 제시하는 다국어 큐레이션 플랫폼

**2026 관광데이터 활용 공모전** — 웹·앱 개발 부문

---

## 소개

'벼리'는 그물의 코들을 꿴 굵은 줄(뼈대)을 뜻하는 순우리말입니다. 흩어진 한복 대여점·전통 디저트 카페·고궁·체험 매장·전통 축제 정보를 하나로 엮어 관광 동선의 '뼈대'를 제시한다는 핵심 가치를 담았습니다.

### 핵심 기능
- 🏯 **한국 전통 테마 통합 큐레이션** — 음식점·카페(디저트)·갤러리·체험·공예 5개 카테고리
- 👘 **한복 혜택 매장 필터** — 한복 착용 시 할인되는 매장만 지도 토글로 표시 (시장 최초)
- 🗺️ **추천 코스 + 여행 일지** — 운영진 큐레이션 코스 & 사용자 직접 구성 일지
- 🌐 **다국어** — 한국어·영어·일본어·중국어 (내·외국인 동시 타깃)
- 🔗 **공공데이터 연동** — 한국관광공사 TourAPI 4.0·문화포털, KOPIS, 카카오맵

---

## 기술 스택

| 구분 | 선택 |
|---|---|
| 백엔드 | Spring Boot (Java) + JPA / QueryDSL |
| 데이터베이스 | PostgreSQL |
| 모바일 앱 | React Native (Expo) |
| 운영자 웹 | Next.js |
| 서버/전역 상태 | TanStack Query + zustand |
| 인프라 | Docker Compose + 단일 VM (nginx · MinIO) |
| 저장소 구성 | 모노레포 |
| DB 마이그레이션 | Flyway |

---

## 저장소 구성

> 현재 단계: **설계 문서 · ERD 확정**. 애플리케이션 코드는 아래 계획 구조에 따라 추가됩니다.

```
byeori/
├─ docs/                         # 📄 기술 설계 문서 (현재 단계 산출물)
│  ├─ byeori-tech-spec.pdf       #    통합 문서 PDF (명명규칙·파일구조·API·인프라)
│  ├─ byeori-tech-spec.md        #    편집용 Markdown 원본
│  ├─ assets/style.css           #    PDF 인쇄 스타일
│  └─ build-pdf.mjs              #    PDF 빌드 스크립트
├─ erd_final_1.txt               # 🗄️ ERD (dbdiagram.io import용, SSOT)
├─ (양식1)…제안서.pdf            # 📋 공모전 제안서
│
└─ apps/                         # (예정) 모노레포 애플리케이션
   ├─ api/                       #    Spring Boot 백엔드 (by-feature)
   ├─ app/                       #    Expo React Native 앱
   └─ web/                       #    Next.js 운영자 어드민
   infra/                        #    docker-compose · nginx · env · scripts
```

---

## 문서 보기 / 빌드

기술 설계 문서는 **명명규칙 → 파일구조 → API 명세 → 인프라 setup** 4개 섹션으로 구성됩니다.

- 📖 **읽기**: [`docs/byeori-tech-spec.pdf`](docs/byeori-tech-spec.pdf)
- ✏️ **수정**: [`docs/byeori-tech-spec.md`](docs/byeori-tech-spec.md) 편집 후 아래로 PDF 재생성

```bash
cd docs
npm install          # 최초 1회 (marked)
node build-pdf.mjs   # → docs/byeori-tech-spec.pdf 생성
```

> PDF 렌더링은 로컬 Chromium(Playwright 캐시)과 NotoSansKR 폰트를 사용합니다.

---

## 기여 · 협업 규칙

브랜치 명명(GitHub Flow)·커밋·PR 규칙은 [`CONTRIBUTING.md`](CONTRIBUTING.md)를 참고하세요.

- `main` 직접 push 금지 → 토픽 브랜치 + PR(1인 리뷰) + Squash 머지
- 브랜치: `<type>/<이슈번호>-<요약>` (예: `feature/12-hanbok-filter`)

---

## 데이터 출처 (이용 의무)

본 서비스는 공공데이터를 활용하며, 다음 출처 표기 의무를 준수합니다.

- **KOPIS** 공연·공연시설 — 출처: (재)예술경영지원센터 공연예술통합전산망, www.kopis.or.kr
- **TourAPI / 문화포털** 음식점·관광지·문화시설·축제 — 출처: 한국관광공사

`source=KOPIS` 또는 `source=TOURAPI` 데이터를 노출하는 화면에는 출처 문구가 필수입니다.
