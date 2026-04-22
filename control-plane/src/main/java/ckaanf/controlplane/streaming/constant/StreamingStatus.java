package ckaanf.controlplane.streaming.constant;

import java.util.Set;

public enum StreamingStatus {
    INIT {
        @Override
        public Set<StreamingStatus> allowNext() {
            return Set.of(STARTING, ERROR);
        }
    },
    STARTING { // FFmpeg 실행 직후

        @Override
        public Set<StreamingStatus> allowNext() {
            return Set.of(STREAMING, FAILED, STOPPED);
        }
    },
    STREAMING { // 실제 송출 데이터 감지 후 (connectedAt 기록 시점)

        @Override
        public Set<StreamingStatus> allowNext() {
            return Set.of(STOPPED, FAILED);
        }
    },
    STOPPED { // 정상 종료 (Terminal State)

        @Override
        public Set<StreamingStatus> allowNext() {
            return Set.of(); // 세션 재사용을 안 한다면 다음 상태 없음
        }
    },
    FAILED { // 비정상 종료 (Terminal State)

        @Override
        public Set<StreamingStatus> allowNext() {
            return Set.of();
        }
    },
    ERROR { // 초기화 실패 (Terminal State)

        @Override
        public Set<StreamingStatus> allowNext() {
            return Set.of();
        }
    };

    public abstract Set<StreamingStatus> allowNext();

    public void canTransitionTo(StreamingStatus next) {
        if (!allowNext().contains(next)) {
            throw new IllegalStateException("잘못된 세션 상태 전이");
        }
    }
}