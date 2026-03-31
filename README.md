# Media Core Lab (미디어 코어 엔지니어링 연구소)

이 프로젝트는 대규모 트래픽과 초저지연(Ultra-Low Latency) 환경에서 미디어 데이터가 어떻게 흘러가고 제어되는지 밑바닥부터 파헤치기 위한 **개인 미디어 인프라 연구소**입니다.

단순한 API 서버를 넘어, C++과 Java(Spring Boot), 그리고 도커 기반의 인프라를 활용해 **어떻게 하면 가장 낮은 시스템 부하로 최고 화질의 라이브 영상을 지연 없이 쏴줄 수 있을까?**를 고민하고 실험합니다.

## Project History (The Pivot)

> **Phase 1 (VOD) ➔ Phase 2 (Live Streaming)**

초기에는 정적인 VOD(Video On Demand) 서비스에서의 파일 I/O 및 스트리밍 성능 최적화(C++ vs Java)에 집중했습니다. 하지만 0.5초의 지연도 용납하지 않는 버츄얼 3D 라이브 및 실시간 인터랙션 시장의 아키텍처에 매력을 느껴, **초저지연 라이브 스트리밍 코어 구축**으로 프로젝트의 방향을 피벗(Pivot)했습니다.

  * [Phase 1: VOD 스트리밍 및 파일 I/O 최적화 연구 기록 보기](vod-server/experiments/results/experiment_io.md)
  * [Phase 2: 초지연 스트리밍도 결국 시청자 입장에선 작은 단위의 정적 파일을 서빙 받는 것이라는 걸 알았음 이걸 고려해서 진짜 **라이브 스트리밍 플랫폼의 기술적 고민이나 기능을 직접 개발하는 걸 도전**

## Architecture & Modules

현재 프로젝트는 라이브 미디어 생태계 구축을 위해 멀티 모듈 기반으로 확장 중입니다.

  * `live-server`: NGINX-RTMP 기반의 라이브 인제스트(Ingest) 코어. 스트리머의 원본 영상을 가장 먼저 받아냅니다.
  * **`control-plane` (Active)**: NGINX의 Webhook(`on_publish`, `on_done`)을 수신하여 FFmpeg 트랜스코딩 프로세스를 비동기로 제어하고 미디어의 라이프사이클을 관리하는 Spring Boot 제어 서버.
  * `live-signaling`: WebRTC 기반의 초저지연 송출 및 시청자 인터랙션을 위한 시그널링 서버 (예정).
  * `vod-transcoder`: 들어온 미디어 트래픽을 실시간으로 다중 화질(1080p, 720p)로 인코딩하는 미디어 워커.
  * `vod-server`: (Legacy) 기존 VOD 스트리밍 서빙 및 실험 환경.
  * `media-common`: 미디어 파이프라인 전반에서 사용되는 공통 유틸리티 및 도메인.

## Current Milestone (진행 중인 과제)

**Step 1: RTMP 인제스트 서버 구축 및 기초 스트리밍 실험**

  - [X] NGINX-RTMP 도커 환경 셋업 (1935 Port)
  - [X] FFmpeg `-re` 옵션을 활용한 로컬 MP4 파일 실시간 RTMP 송출 (Mocking Live Stream)
  - [X] VLC 플레이어를 통한 라이브 스트리밍 정상 수신 확인

**Step 2: OBS 실시간 연동 및 단일 화질(MVP) 파이프라인 뚫기 (완료)**

  - [X] OBS Studio 실시간 RTMP 송출 연동 (하드웨어 인코더 QSV/NVENC 적용)
  - [X] NGINX `chunk_size` 병목 확장을 통한 고화질 비디오 프레임 누락(Drop) 현상 해결
  - [X] Spring Boot(`control-plane`) 기반 FFmpeg 자식 프로세스 격리 및 스레드 풀 관리
  - [X] 웹 브라우저 호환성을 위한 픽셀 포맷(`yuv420p`) 및 엄격한 키프레임(`-g 60`) 주입 적용
  - [X] 브라우저(HLS.js)와 연동하여 End-to-End 단일 화질 라이브 방송 출력 성공 및 좀비 프로세스 방어

**Step 3: ABR(다중 화질) 고도화 및 플랫폼 아키텍처 확장 (Next)**

  - [ ] FFmpeg `-filter_complex`를 활용한 단일 스트림 ➡️ 다중 화질(1080p, 720p, 480p) 실시간 분기 처리
  - [ ] 스트리머 대시보드(DB 연동) 기반의 방송 메타데이터(제목, 카테고리) 관리 설계
  - [ ] 방송 종료 시 HLS(`.ts`) 파편 파일들을 단일 `.mp4` 파일로 병합하여 VOD로 저장하는 로직 구현
  - [ ] 미디어 코덱(H.264/GOP) 및 파이프라인 트러블슈팅 과정 문서화 및 블로깅

## 🛠 Tech Stack

  - **Backend Core**: Java (Spring Boot), C++
  - **Media Engine**: FFmpeg, NGINX-RTMP
  - **Infrastructure**: Docker
