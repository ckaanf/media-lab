# Media Lab: High-Performance Streaming Research

본 프로젝트는 2년 차 백엔드 개발자에서 **미디어 엔지니어**로 도약하기 위한 1년 단위의 실무 연구 및 구현 기록입니다. 단순 구현을 넘어 Java/C++ 하이브리드 아키텍처와 커널 레벨 최적화를 통해 미디어 도메인의 기술적 임계점을 돌파하는 과정을 탐구합니다.

---

## 1. Media Architecture Overview

실무 수준의 시스템 구축을 위해 다음 4가지 레이어를 단계적으로 구현하고 통합합니다.

* **Ingest Layer**: RTMP/SRT 프로토콜 수신 및 패킷 추출 (C++ 핵심)
* **Processing Layer**: 실시간/VOD 트랜스코딩 및 필터링 (FFmpeg API & JNI)
* **Origin Layer**: HLS/CMAF 패키징 및 고속 전송 (Java NIO/Zero-copy)
* **Delivery Layer**: Redis 및 Edge Caching을 통한 트래픽 분산 (WebFlux)

---

## 2. 12-Month Roadmap (Phase 1)

각 단계별 상세 태스크는 [로드맵 상세]() 섹션에서 확인할 수 있습니다.

### [1-2개월] Ingest: 하이브리드 엔진 인터페이스 구축

* C++ 기반 FFmpeg API 연동 및 RTMP/SRT 수신 뼈대 구축
* Java-C++ 데이터 전달 방식(JNI, Shared Memory, gRPC) 비교 실험

### [3-4개월] Processing: 실시간 패키징 파이프라인

* FFmpeg C API 활용 실시간 Multi-bitrate 트랜스코딩 구현
* GOP(Group of Pictures) 구조 최적화 및 HLS Segmenter 구현

### [5-6개월] Origin/Delivery: 고속 서빙 및 ABR 검증

* Spring WebFlux 비동기 서빙 및 Redis 기반 캐시 전략 적용
* 네트워크 가변성 대응을 위한 ABR 알고리즘 수치 검증

### [7-12개월] Optimization: 극한의 고도화

* `io_uring` 도입을 통한 I/O 한계 돌파 혹은 HTTP/3 커스텀 전송 최적화

---

## 3. Performance Experiments (실험 일지)

지속적인 벤치마킹을 통해 I/O 모델과 동시성 구조의 차이를 기록합니다. 상세 내용은 [종합 분석 리포트](./vod-server/experiments/results/experiment_io.md)를 참조하십시오.

### [실험 1] Chunk Size 및 User-space Copy 병목 분석

* **현상**: Java(Standard IO)는 청크가 1MB로 증가 시 메모리 복사 비용으로 P95 Latency 156% 급증
* **대조**: C++은 `sendfile` 기반 Zero-copy로 청크 크기에 관계없이 일관된 지연 시간 유지

### [실험 2] 동시 접속자 수(VUs)에 따른 확장성 테스트

* **현상**: Java 서버는 500 VUs 구간에서 스레드 경합으로 Tail Latency 43배 폭증
* **대조**: C++은 Non-blocking IO 모델로 3.8ms의 안정적 지연 시간 및 선형적 확장성 증명

### [실험 3] Java Zero-copy(transferTo) 최적화 검증

* **결과**: `FileChannel.transferTo()` 적용 시 기존 방식 대비 P95 Latency 28% 개선
* **시사점**: Zero-copy 도입 시 유의미한 성능 향상이 가능하나, JVM 런타임 오버헤드가 추가 병목임을 확인

---

## 4. Tech Stack & Project Structure

* **Languages**: C++ (Epoll, Zero-copy), Java (Spring Boot, NIO)
* **Environment**: WSL2 (Ubuntu 22.04), Docker, k6

### Directory Structure

* `vod-server/cpp`: 고성능 비동기 스트리밍 서버 연구 (C++)
* `vod-server/java`: Java 기반 IO 모델 성능 분석 및 최적화 연구 (Java)
* `vod-server/experiments`: 성능 테스트 자동화 스크립트 및 결과 리포트

---

## 부록: 로드맵 상세 가이드

### Phase 1 상세 태스크

* **Task 1.1**: C++ 기반 FFmpeg API 연동 및 RTMP/SRT 수신 뼈대 작성
* **Task 1.2**: JNI 실험 - DirectByteBuffer를 활용한 C++ to Java 데이터 전달 비용 측정
* **Task 1.3**: Shared Memory 실험 - 커널 레벨 공유 메모리 기반 IPC 구현 및 성능 테스트
* **Task 1.4**: gRPC/UDS 실험 - Control Plane과 Data Plane 분리 설계 분석
* **Task 3.3**: `tc`(Traffic Control) 도구를 활용한 네트워크 가변성 환경에서의 ABR 검증

---
