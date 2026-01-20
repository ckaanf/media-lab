#  Media Lab: High-Performance Streaming Research

미디어 스트리밍 기술의 효율성을 심층 연구하고, 기술 스택별(Java, C++) 성능 병목 지점을 데이터로 분석하여 최적화 모델을 제시하는 실험실입니다.

##  Performance Experiments (실험 일지)

지속적인 벤치마킹을 통해 IO 처리 모델과 동시성 구조의 차이를 기록합니다.

###  [IO 병목 및 확장성 종합 분석](./vod-server/experiments/results/experiment_io.md)

#### 1️ 실험 1: Chunk Size 변화에 따른 복사 오버헤드 분석
*   **주제**: 데이터 청크 크기(64KB vs 1MB)가 전송 효율에 미치는 영향
*   **결과**: Java는 청크가 커질수록 User-space 메모리 복사 비용이 누적되어 **P95 Latency가 156% 급증**. 반면 C++은 `sendfile` 기반 Zero-copy로 **일관된 지연 시간** 유지.

####  실험 2: 동시 접속자 수(VUs)에 따른 확장성(Scalability) 분석
*   **주제**: 고부하 상황(100~500 VUs)에서 서버 모델별 응답 안정성 테스트
*   **결과**: Java 서버는 500 VUs 구간에서 스레드 경합 및 메모리 오버헤드로 인해 **Tail Latency가 약 43배 폭증(3.6ms → 154.9ms)**. C++은 Non-blocking IO 모델을 통해 **3.8ms의 안정적 지연 시간**과 4,390 Req/s의 선형적 확장성 증명.

---

##  Tech Stack
- **Languages**: C++ (Epoll, Zero-copy), Java (Spring Boot)
- **Environment**: WSL2 (Ubuntu 22.04), Docker
- **Testing**: k6

##  Project Structure
- `vod-server/cpp`: 고성능 비동기 스트리밍 서버 연구 (C++)
- `vod-server/java`: 일반적인 웹 서버 모델의 성능 지표 분석 (Java)
- `vod-server/experiments`: 성능 테스트 스크립트 및 결과 리포트