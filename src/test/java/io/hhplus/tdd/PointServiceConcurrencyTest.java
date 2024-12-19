package io.hhplus.tdd;

import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
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
    @DisplayName("동시성 환경에서 한 사용자가 여러 기능을 호출했을 시 순서대로 올바르게 동작하는지 테스트")
    void testConcurrentChargeAndUse() throws InterruptedException {
        long userId = 1L;
        int numberOfThreads = 10;
        long chargeAmount = 200L;
        long useAmount = 100L;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        ReentrantLock lock = new ReentrantLock();

        for (int i = 0; i < numberOfThreads; i++) {
            if (i % 2 == 0) {
                executorService.submit(() -> {
                    lock.lock();
                    try {
                        pointService.chargePoints(userId, chargeAmount);
                    } finally {
                        lock.unlock();
                        latch.countDown();
                    }
                });
            } else {
                executorService.submit(() -> {
                    lock.lock();
                    try {
                        pointService.usePoints(userId, useAmount);
                    } finally {
                        lock.unlock();
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();
        executorService.shutdown();

        UserPoint resultUserPoint = pointService.getUserPoints(userId);
        assertThat(resultUserPoint.point()).isEqualTo(500);
    }

    @Test
    @DisplayName("동시성 환경에서 여러 ID가 충전하는 테스트")
    void testConcurrentForUsers() throws InterruptedException {
        int numberOfThreads = 3;
        long chargeAmount = 200L;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads * 2);

        List<Long> users = List.of(1L, 2L, 3L);

        for (long userId : users) {
            for (int i = 0; i < 2; i++) {
                executorService.submit(() -> {
                    try {
                        pointService.chargePoints(userId, chargeAmount);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();
        executorService.shutdown();

        for (long userId : users) {
            UserPoint resultUserPoint = pointService.getUserPoints(userId);
            assertThat(resultUserPoint.point()).isEqualTo(chargeAmount * 2);
        }

    }
}

