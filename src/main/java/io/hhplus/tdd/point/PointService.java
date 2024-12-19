package io.hhplus.tdd.point;


import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final Lock lock = new ReentrantLock();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    public UserPoint chargePoints(long userId, long amount) {
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);
            long newAmount = userPoint.point() + amount;
            if (newAmount > 1_000_000) {
                throw new IllegalArgumentException("잔고가 최대 한도를 초과할 수 없습니다.");
            }
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            return updatedUserPoint;
        } finally {
            lock.unlock();
        }
    }

    public UserPoint usePoints(long userId, long amount) {
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);
            if (userPoint.point() < amount) {
                throw new IllegalArgumentException("잔고가 부족합니다.");
            }
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, userPoint.point() - amount);
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return updatedUserPoint;
        } finally {
            lock.unlock();
        }
    }

    public UserPoint getUserPoints(long userId) {
        return userPointTable.selectById(userId);
    }

    public List<PointHistory> getUserPointHistories(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }
}
