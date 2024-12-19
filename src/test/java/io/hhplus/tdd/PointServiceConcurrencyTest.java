package io.hhplus.tdd;

import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import io.hhplus.tdd.point.repository.UserPointRepository;
import org.springframework.boot.test.context.SpringBootTest;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

    @SpringBootTest
    class PointServiceConcurrencyTest {

        private PointService pointService;
        private UserPointTable userPointTable;
        private PointHistoryTable pointHistoryTable;

        @BeforeEach
        void setUp() {
            userPointTable = new UserPointTable();
            pointHistoryTable = new PointHistoryTable();
            pointService = new PointService(userPointTable, pointHistoryTable);
        }

        @Test
        void concurrentChargeAndUsePoints() throws InterruptedException {
            long userId = 1L;
            int numberOfThreads = 10;
            long chargeAmount = 100;
            long useAmount = 50;

            ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch latch = new CountDownLatch(numberOfThreads);

            for (int i = 0; i < numberOfThreads; i++) {
                if (i % 2 == 0) {
                    executorService.submit(() -> {
                        try {
                            pointService.chargePoints(userId, chargeAmount);
                        } finally {
                            latch.countDown();
                        }
                    });
                } else {
                    executorService.submit(() -> {
                        try {
                            pointService.usePoints(userId, useAmount);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }

            latch.await();
            executorService.shutdown();

            UserPoint finalUserPoint = pointService.getUserPoints(userId);
            assertThat(finalUserPoint.point()).isEqualTo(250);
        }
}

