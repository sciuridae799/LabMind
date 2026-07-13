package com.labmind.idgenerator.toolkit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkDataCenterIdTest {

    @Test
    void shouldAcceptBoundaryValues() {
        WorkDataCenterId workDataCenterId = new WorkDataCenterId(0, 31);

        assertThat(workDataCenterId.workId()).isEqualTo(0);
        assertThat(workDataCenterId.dataCenterId()).isEqualTo(31);
    }

    @Test
    void shouldRejectOutOfRangeWorkId() {
        assertThatThrownBy(() -> new WorkDataCenterId(32, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workId");
    }

    @Test
    void shouldRejectOutOfRangeDataCenterId() {
        assertThatThrownBy(() -> new WorkDataCenterId(1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataCenterId");
    }
}
