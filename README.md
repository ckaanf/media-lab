# Media Lab: High-Performance Streaming Research

미디어 스트리밍 기술을 학습하고, 
각 기술이 갖는 장단점에 대해 비교하고 탐구합니다.

## Performance Experiments (실험 일지)

지속적인 벤치마킹을 통해 IO 처리 모델과 동시성 구조의 차이를 기록합니다.

### [IO 병목 및 확장성 종합 분석 리포트](./vod-server/experiments/results/experiment_io.md)

#### 실험 1: Chunk Size 및 User-space Copy 병목 분석
*   **현상**: Java(Standard IO)는 청크가 1MB로 커질 때 메모리 복사 비용으로 인해 **P95 Latency가 156% 급증**.
*   **대조**: C++은 `sendfile` 기반 Zero-copy로 청크 크기에 관계없이 **일관된 지연 시간** 유지.

#### 실험 2: 동시 접속자 수(VUs)에 따른 확장성(Scalability) 테스트
*   **현상**: Java 서버는 500 VUs 구간에서 스레드 경합 및 복사 오버헤드로 **Tail Latency 43배 폭증**.
*   **대조**: C++은 Non-blocking IO 모델로 **3.8ms의 안정적 지연 시간**과 선형적 확장성(4,390 Req/s) 증명.

#### 실험 3: Java Zero-copy(`transferTo`) 최적화 및 검증 
*   **주제**: Java에서 `FileChannel.transferTo()`를 적용하여 메모리 복사 비용 제거 시 성능 변화 측정.
*   **결과**: 기존 Read/Write 방식 대비 **P95 Latency 약 28% 개선 (154.9ms → 110.9ms)**.
*   **시사점**: Java 환경에서도 Zero-copy 도입 시 유의미한 성능 향상이 가능하나, C++과의 잔여 격차를 통해 서블릿 컨테이너 및 JVM 런타임 오버헤드가 추가 병목임을 확인.

---

## Tech Stack
- **Languages**: C++ (Epoll, Zero-copy), Java (Spring Boot, NIO)
- **Environment**: WSL2 (Ubuntu 22.04), Docker
- **Testing**: k6, Custom Shell Scripts

## Project Structure
- `vod-server/cpp`: 고성능 비동기 스트리밍 서버 연구 (C++)
- `vod-server/java`: Java 기반 IO 모델 성능 분석 및 최적화 연구 (Java)
- `vod-server/experiments`: 성능 테스트 자동화 스크립트 및 결과 리포트
