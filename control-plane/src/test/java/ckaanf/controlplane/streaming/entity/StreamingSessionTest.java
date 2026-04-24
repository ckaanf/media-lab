package ckaanf.controlplane.streaming.entity;

import ckaanf.controlplane.streaming.live.domain.constant.StopReason;
import ckaanf.controlplane.streaming.live.domain.constant.StreamingStatus;
import ckaanf.controlplane.streaming.common.exception.StreamingDomainException;
import ckaanf.controlplane.streaming.live.domain.entity.StreamingSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingSessionTest {

    @Test
    @DisplayName("세션이 시작되면 상태가 STARTING으로 변하고 PID와 시작 시간이 기록된다")
    void session_Start_Success() {
        // given
        StreamingSession session = createInitSession();

        // when
        session.start(12345L, "command");

        // then
        assertThat(session.getStatus()).isEqualTo(StreamingStatus.STARTING);
        assertThat(session.getProcess().getPid()).isEqualTo(12345L);
        assertThat(session.getLifecycle().getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("STARTING 상태에서 connect 호출 시 STREAMING 상태로 변하고 연결 시간이 기록된다")
    void session_Connect_Success() {
        // given
        StreamingSession session = createInitSession();
        session.start(12345L, "command");

        // when
        session.connect();

        // then
        assertThat(session.getStatus()).isEqualTo(StreamingStatus.STREAMING);
        assertThat(session.getLifecycle().getConnectedAt()).isNotNull();
    }

    @Test
    @DisplayName("STREAMING 상태에서 stop 호출 시 STOPPED 상태로 변하고 종료 처리된다")
    void session_Stop_Success() {
        // given
        StreamingSession session = createInitSession();
        session.start(12345L, "command");
        session.connect();

        // when
        session.stop(StopReason.USER_STOP);

        // then
        assertThat(session.getStatus()).isEqualTo(StreamingStatus.STOPPED);
        assertThat(session.getLifecycle().getEndedAt()).isNotNull();
        assertThat(session.getProcess().getPid()).isNull();
        assertThat(session.getResult().getStopReason()).isEqualTo(StopReason.USER_STOP);
    }

    @Test
    @DisplayName("STARTING 상태에서 fail 호출 시 FAILED 상태로 변하고 종료 처리된다")
    void session_Fail_Success() {
        // given
        StreamingSession session = createInitSession();
        session.start(12345L, "command");

        // when
        session.fail(StopReason.PROCESS_EXIT, "ffmpeg exit with error");

        // then
        assertThat(session.getStatus()).isEqualTo(StreamingStatus.FAILED);
        assertThat(session.getLifecycle().getEndedAt()).isNotNull();
        assertThat(session.getProcess().getPid()).isNull();
        assertThat(session.getResult().getErrorMessage()).isEqualTo("ffmpeg exit with error");
    }

    @Test
    @DisplayName("INIT 상태에서 바로 connect를 호출하면 IllegalStateException이 발생한다")
    void session_Connect_Fail_InvalidState() {
        // given
        StreamingSession session = createInitSession();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(StreamingDomainException.class, session::connect);
    }

    private StreamingSession createInitSession() {
        return StreamingSession.builder()
                .streamKey("test-stream-key")
                .inputUrl("rtmp://localhost/live/test")
                .outputPath("/tmp/hls")
                .build();
    }
}