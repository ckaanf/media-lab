# 🧪 Media Lab: High-Performance Streaming Research

미디어 스트리밍 기술의 기초를 학습하고
나아가 다양한 기술 스택(Java, C++)을 통해 성능 병목 지점을 분석하고 최적화하는 실험실입니다.

## 📝 Performance Experiments (실험 일지)

지속적인 성능 테스트와 벤치마킹 결과를 기록합니다.

### 1. [IO 병목 분석: Zero-copy vs User-space Copy](./vod-server/experiments/results/experiment_io.md)
*   **실험 주제**: 대용량 파일 전송 시 Chunk Size 변화가 Java와 C++ 서버 성능에 미치는 영향
*   **핵심 발견**:
    *   Java는 Chunk Size가 1MB로 커질 때 **P95 Latency가 156% 급증**하며 User-space 복사 오버헤드 증명.
    *   C++은 `sendfile`을 통해 청크 크기에 관계없이 **일관된 Latency** 유지 (Zero-copy 효율성 확인).
*   **결론**: 고성능 VOD 스트리밍에서는 커널 레벨의 Zero-copy 처리가 Tail Latency 관리에 용이함.

---
