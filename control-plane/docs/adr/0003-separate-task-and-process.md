# ADR-0002: 도메인 내 인프라 종속성 제거 (ProcessChecker)

## 상태
Accepted (승인됨)

## 맥락 (Context)
- `StreamingSession` 엔티티가 실제 실행 중인 FFmpeg 프로세스의 생존 여부를 확인해야 함.
- 엔티티 내부에서 직접 `java.lang.Process`나 `ProcessHandle` API를 사용하면 단위 테스트가 어렵고, 실행 환경(Docker, K8s 등) 변화에 유연하게 대응하기 힘듦.

## 결정 (Decision)
- **도메인 서비스/포트 도입**: `ProcessChecker` 인터페이스를 도메인 계층에 정의한다.
- **의존성 역전(DIP)**: 실제 구현체(`JavaProcessChecker`)는 인프라 계층에 위치시키고, 엔티티는 메서드 파라미터로 인터페이스를 주입받아 사용하도록 설계한다.

## 결과 (Consequences)
- 엔티티는 OS의 구체적인 구현 기술을 몰라도 비즈니스 검증 로직을 수행할 수 있음.
- 테스트 시 Mock 객체를 통해 프로세스가 죽은 상황을 자유롭게 시뮬레이션할 수 있어 단위 테스트 신뢰도가 향상됨.