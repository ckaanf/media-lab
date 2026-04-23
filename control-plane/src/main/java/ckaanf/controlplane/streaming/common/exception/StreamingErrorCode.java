package ckaanf.controlplane.streaming.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StreamingErrorCode {
    // [S] Session 관련: 비즈니스 로직 및 상태 무결성
    SESSION_NOT_FOUND("S-001", "세션 정보를 찾을 수 없습니다. (ID: %s)"),
    INVALID_STATUS_TRANSITION("S-002", "허용되지 않는 상태 전환입니다. (현재: %s, 요청: %s)"),
    SESSION_ALREADY_ACTIVE("S-003", "이미 활성화된 세션입니다. (StreamKey: %s)"),
    SESSION_ALREADY_TERMINATED("S-004", "이미 종료된 세션입니다. (ID: %s)"),

    // [P] Process 관련: 외부 엔진(FFmpeg) 제어 및 생명주기
    PROCESS_START_FAILED("P-001", "FFmpeg 프로세스 기동에 실패했습니다."),
    PROCESS_ORPHANED("P-002", "프로세스 정보는 존재하나 실제 실행 중이 아닙니다. (PID: %s)"),
    PROCESS_TERMINATED_ABNORMALLY("P-003", "프로세스가 비정상 종료되었습니다. (ExitCode: %d)"),
    PROCESS_EXECUTION_TIMEOUT("P-004", "프로세스 실행 후 응답 대기 시간이 초과되었습니다.");

    private final String code;
    private final String message;
}
