package ckaanf.controlplane.streaming.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StreamingStatusTest {
    @Test
    @DisplayName("INIT 상태에서 STARTING 상태로 정상 전이된다")
    void transition_Success() {
        // given
        StreamingStatus currentStatus = StreamingStatus.INIT;

        // when & then (예외가 발생하지 않아야 함)
        assertThatCode(() -> currentStatus.canTransitionTo(StreamingStatus.STARTING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이 시 IllegalStateException이 발생한다")
    void transition_Fail_ThrowsException() {
        // given
        StreamingStatus currentStatus = StreamingStatus.STREAMING;

        // when & then (STREAMING에서 다시 STARTING으로 갈 수 없음)
        assertThatThrownBy(() -> currentStatus.canTransitionTo(StreamingStatus.STARTING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잘못된 세션 상태 전이");
    }

    @Test
    @DisplayName("터미널 상태(STOPPED, FAILED, ERROR)에서는 다른 상태로 전이할 수 없다")
    void terminalStates_CannotTransition() {
        assertThat(StreamingStatus.STOPPED.allowNext()).isEmpty();
        assertThat(StreamingStatus.FAILED.allowNext()).isEmpty();
        assertThat(StreamingStatus.ERROR.allowNext()).isEmpty();

        assertThatThrownBy(() -> StreamingStatus.STOPPED.canTransitionTo(StreamingStatus.INIT))
                .isInstanceOf(IllegalStateException.class);
    }

}