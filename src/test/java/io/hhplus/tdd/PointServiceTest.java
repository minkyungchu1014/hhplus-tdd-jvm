package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;
    @Mock
    private PointHistoryTable pointHistoryTable;

    private PointService pointService;

    @BeforeEach
    void setUp() {
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    @DisplayName("유저 포인트 조회 테스트")
    void testGetUser() {
        long userId = 1L;
        long pointAmount = 300L;

        UserPoint userPoint = new UserPoint(userId, pointAmount, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        UserPoint result = pointService.getUserPoints(userId);

        assertThat(result).isEqualTo(userPoint);
        verify(userPointTable).selectById(userId);
    }

    @Test
    @DisplayName("포인트 충전 성공 테스트")
    void testChargePoints_Success() {
        long userId = 1L;
        long amount = 500L;

        UserPoint userPoint = new UserPoint(userId, 100L, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, 600L, System.currentTimeMillis());

        when(userPointTable.selectById(userId)).thenReturn(userPoint);
        when(userPointTable.insertOrUpdate(userId, 600L)).thenReturn(updatedUserPoint);

        UserPoint result = pointService.chargePoints(userId, amount);

        assertThat(result.point()).isEqualTo(600L);
        verify(userPointTable).insertOrUpdate(userId, 600L);
    }

    @Test
    @DisplayName("포인트 충전 시 최대 한도 초과 테스트")
    void testChargePoints_MaxLimitException(){
        long userId = 1L;
        long initialPoint = 900_000L;
        long chargeAmount = 300_000L;

        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        assertThatThrownBy(() -> pointService.chargePoints(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("잔고가 최대 한도를 초과할 수 없습니다.");

        verify(userPointTable).selectById(userId);
    }

    @Test
    @DisplayName("포인트 사용 성공 테스트")
    void testUsePoints_Success(){
        long userId = 1L;
        long initialPoint = 500L;
        long useAmount = 300L;

        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, initialPoint - useAmount, System.currentTimeMillis());

        when(userPointTable.selectById(userId)).thenReturn(userPoint);
        when(userPointTable.insertOrUpdate(userId, initialPoint - useAmount)).thenReturn(updatedUserPoint);

        UserPoint result = pointService.usePoints(userId, useAmount);

        assertThat(result.point()).isEqualTo(initialPoint - useAmount);
        verify(userPointTable).insertOrUpdate(userId, initialPoint - useAmount);
    }

    @Test
    @DisplayName("포인트 사용 시 잔고가 부족할 경우 예외 발생 검증 테스트")
    void testUsePoints_InsufficientException() {
        long userId = 1L;
        long amount = 500L;

        UserPoint userPoint = new UserPoint(userId, 300L, System.currentTimeMillis());

        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pointService.usePoints(userId, amount)
        );

        assertEquals("잔고가 부족합니다.", exception.getMessage());
        verify(userPointTable).selectById(userId);
    }

    @Test
    @DisplayName("유저 포인트 이력 조회 테스트")
    void getUserHistory_Success() {
        long userId = 1L;
        PointHistory history1 = new PointHistory(1L, userId, 500L, TransactionType.CHARGE, System.currentTimeMillis());
        PointHistory history2 = new PointHistory(2L, userId, 100L, TransactionType.CHARGE, System.currentTimeMillis());

        List<PointHistory> historyList = List.of(history1, history2);

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(historyList);

        List<PointHistory> result = pointService.getUserPointHistories(userId);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0)).isEqualTo(history1);
        assertThat(result.get(1)).isEqualTo(history2);
        verify(pointHistoryTable).selectAllByUserId(userId);

    }
}


