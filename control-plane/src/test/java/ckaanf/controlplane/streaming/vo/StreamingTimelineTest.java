package ckaanf.controlplane.streaming.vo;

import ckaanf.controlplane.streaming.live.domain.vo.StreamingTimeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingTimelineTest {
    @Test
    @DisplayName("상태 변경 메서드를 호출하면 기존 객체가 아닌 새로운 객체를 반환한다 (불변성 보장)")
    void immutability_Check() {
        // given
        StreamingTimeline initTimeline = StreamingTimeline.init();

        // when
        StreamingTimeline startedTimeline = initTimeline.withStarted();

        // then
        assertThat(startedTimeline).isNotSameAs(initTimeline);
        assertThat(initTimeline.getStartedAt()).isNull();
        assertThat(startedTimeline.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("시작 시각과 연결 시각이 모두 있으면 지연 시간을 밀리초로 반환한다")
    void getStartupLatency_Success() throws InterruptedException {
        // given
        StreamingTimeline timeline = StreamingTimeline.init().withStarted();

        Thread.sleep(100); // 100ms 대기 (실제로는 Mocking하거나 Instant를 주입받는 구조가 더 좋음)

        timeline = timeline.withConnected();

        // when
        Long latency = timeline.getStartupLatencyMillis();

        // then
        assertThat(latency).isNotNull();
        assertThat(latency).isGreaterThanOrEqualTo(100L);
    }

    @Test
    @DisplayName("시작 시각이나 연결 시각 중 하나라도 없으면 지연 시간으로 null을 반환한다")
    void getStartupLatency_Null_WhenMissingTimes() {
        // given
        StreamingTimeline onlyStarted = StreamingTimeline.init().withStarted();
        StreamingTimeline onlyConnected = StreamingTimeline.init().withConnected();

        // when & then
        assertThat(onlyStarted.getStartupLatencyMillis()).isNull();
        assertThat(onlyConnected.getStartupLatencyMillis()).isNull();
    }

}