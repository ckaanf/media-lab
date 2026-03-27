# 🎥 Media Core Lab (미디어 코어 엔지니어링 연구소)

이 프로젝트는 대규모 트래픽과 초저지연(Ultra-Low Latency) 환경에서 미디어 데이터가 어떻게 흘러가고 제어되는지 밑바닥부터 파헤치기 위한 **개인 미디어 인프라 연구소**입니다.

단순한 API 서버를 넘어, C++과 Java(Spring Boot), 그리고 도커 기반의 인프라를 활용해 **"어떻게 하면 가장 낮은 시스템 부하로 최고 화질의 라이브 영상을 지연 없이 쏴줄 수 있을까?"**를 치열하게 고민하고 실험합니다.

## 🔄 Project History (The Pivot)
> **Phase 1 (VOD) ➔ Phase 2 (Live Streaming)**

초기에는 정적인 VOD(Video On Demand) 서비스에서의 파일 I/O 및 스트리밍 성능 최적화(C++ vs Java)에 집중했습니다. 하지만 0.5초의 지연도 용납하지 않는 버츄얼 3D 라이브 및 실시간 인터랙션 시장의 아키텍처에 매력을 느껴, **초저지연 라이브 스트리밍 코어 구축**으로 프로젝트의 방향을 피벗(Pivot)했습니다.

* 📜 [Phase 1: VOD 스트리밍 및 파일 I/O 최적화 연구 기록 보기](./OLD_README_VOD.md)

## 🏗️ Architecture & Modules

현재 프로젝트는 라이브 미디어 생태계 구축을 위해 멀티 모듈 기반으로 확장 중입니다.

* `live-server` (진행 중): NGINX-RTMP 기반의 라이브 인제스트(Ingest) 코어. 스트리머의 원본 영상을 가장 먼저 받아냅니다.
* `live-signaling`: WebRTC 기반의 초저지연 송출 및 시청자 인터랙션을 위한 시그널링 서버.
* `vod-transcoder`: 들어온 미디어 트래픽을 FFmpeg 등을 이용해 실시간으로 다중 화질(1080p, 720p)로 인코딩하는 제어 모듈.
* `vod-server`: (Legacy) 기존 VOD 스트리밍 서빙 및 실험 환경.
* `media-common`: 미디어 파이프라인 전반에서 사용되는 공통 유틸리티 및 도메인.

## 🚀 Current Milestone (진행 중인 과제)
**Step 1: RTMP 인제스트 서버 구축 및 "가짜 라이브(Fake Live)" 스트리밍 실험**
- [X] NGINX-RTMP 도커 환경 셋업 (1935 Port)
- [X] FFmpeg `-re` 옵션을 활용한 로컬 MP4 파일 실시간 RTMP 송출 (Mocking Live Stream)
- [X] VLC 플레이어를 통한 라이브 스트리밍 정상 수신 확인

**Step 2: RTMP 영상 수신 후 HLS 송출 / 재산화 고려 / 도메인 학습**
- [X] Java Server로 FFmpeg 제어
- [ ] 방송 종료 후 HLS를 VOD로 제작 (다시보기 등)
- [ ] 고민 중

## 🛠 Tech Stack
- **Backend Core**: Java (Spring Boot), C++
- **Media Engine**: FFmpeg, NGINX-RTMP, WebRTC
- **Infrastructure**: Docker, AWS (예정)
