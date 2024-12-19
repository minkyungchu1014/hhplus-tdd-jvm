package io.hhplus.tdd.point;


import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
@Service
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointService.class);
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ConcurrentHashMap<Long, ReentrantLock> perUserLocks = new ConcurrentHashMap<>();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    private Lock getLockForUser(long userId) {
        return perUserLocks.computeIfAbsent(userId, id -> new ReentrantLock());
    }

    public UserPoint chargePoints(long userId, long amount) {
        Lock lock = getLockForUser(userId);
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
        } catch (Exception e){
            log.error("{}:{}", userId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public UserPoint usePoints(long userId, long amount) {
        Lock lock = getLockForUser(userId);
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);
            if (userPoint.point() < amount) {
                throw new IllegalArgumentException("잔고가 부족합니다.");
            }
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, userPoint.point() - amount);
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return updatedUserPoint;
        } catch (Exception e){
            log.error("{}:{}", userId, e.getMessage());
            throw e;
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
