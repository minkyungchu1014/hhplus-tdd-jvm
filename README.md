동시성 제어 방식에 대한 분석 및 보고서
================================
###### 본 보고서는 동시성 문제를 발견하고 이를 해결하기 위한 단계별 접근 방법과 각 방식의 장단점을 분석하며, 최종적으로 ID별 동시성을 보장하는 최적화된 동시성 제어를 구현한 과정을 설명합니다.

--------------------------------


## 1. 동시성이 깨지는 경우 발견
### 문제 상황
다수의 스레드가 동시에 포인트를 충전하거나 사용할 때, 동시성 문제가 발생했습니다. 특히, 동일한 사용자 ID에 대해 여러 작업이 동시에 수행될 경우, 데이터가 일관되지 않게 업데이트되는 문제가 나타났습니다.
   
### 코드 예시
```
public UserPoint chargePoints(long userId, long amount) {
    UserPoint userPoint = userPointTable.selectById(userId);
    long newAmount = userPoint.point() + amount;
    userPointTable.insertOrUpdate(userId, newAmount);
    return userPoint;
}
```

### 문제점
- 여러 스레드가 동시에 메소드를 호출하면서 충전/사용 과정에서 다른 스레드의 업데이트가 덮어씌워지는 문제가 발생함
- 데이터의 일관성이 깨지며, 결과적으로 계산이 이상하게됨.

## 2. 해결 방법
1. 1차 시도 : synchronized 키워드 사용

```
public synchronized UserPoint chargePoints(long userId, long amount) {
    UserPoint userPoint = userPointTable.selectById(userId);
    long newAmount = userPoint.point() + amount;
    userPointTable.insertOrUpdate(userId, newAmount);
    return userPoint;
}
```
### 장점
- 매우 간단하게 해결 가능함.
- 한 번에 하나의 스레드만 접근하므로 데이터의 일관성이 보장됨.
### 문제점
- 성능 저하( 한 번에 하나의 스레드만 처리할 수 있어 병렬처리가 아니기 때문에 속도가 느림.)
- ID별 동시성 제어 불가능(모든 사용자에 대해 전체 메소드를 동기화하므로, 서로 다른 사용자에 대한 요청도 동시에 처리되지 않음)
- 
2. 2차시도 : 검색하다 찾은 ReentrantLock 사용
### 해결 코드
```
private final ReentrantLock lock = new ReentrantLock();

public UserPoint chargePoints(long userId, long amount) {
    lock.lock();
    try {
        UserPoint userPoint = userPointTable.selectById(userId);
        long newAmount = userPoint.point() + amount;
        userPointTable.insertOrUpdate(userId, newAmount);
        return userPoint;
    } finally {
        lock.unlock();
    }
}

```

### 장점
- 동기화 블럭을 더 세밀하게 제어할 수 있음.
- 공정성이나 타임아웃을 설정할 수 있음.

### 문제점
- ID별 동시성 제어 불가 -> 하나의 락을 사용하므로, 여전히 모든 사용자에 대해 순차적으로 처리됨.
- 특정사용자에게 락을 걸 수 없음.

3. 3차 시도 : ConcurrentHashMap과 ReentrantLock을 사용한 ID별 동시성 제어
### 해결 코드
```angular2html
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

private Lock getLockForUser(long userId) {
    return userLocks.computeIfAbsent(userId, id -> new ReentrantLock());
}

public UserPoint chargePoints(long userId, long amount) {
    Lock lock = getLockForUser(userId);
    lock.lock();
    try {
        UserPoint userPoint = userPointTable.selectById(userId);
        long newAmount = userPoint.point() + amount;
        userPointTable.insertOrUpdate(userId, newAmount);
        return userPoint;
    } finally {
        lock.unlock();
    }
}

```
### 장점
- 각 사용자 ID에 대해 별도의 락을 사용 (ID를 키값으로 이용함)
- 동일한 사용자에 대한 작업은 순차적으로, 다른 사용자에 대한 작업은 동시에 처리될 수 있음.
- 병렬 처리가 가능하기 때문에 처리 성능이 향상됨.
  
### 문제점
- 락을 걸어야할 객체가 많아지면 메모리 사용량이 증가될 것으로 보임


## 3. 최종 분석 및 결론

### 최종 해결 방법 : ConcurrentHashMap과 ReentrantLock을 사용한 ID별 동시성 제어
### 결론

#### 본 과정을 통해 동시성 문제를 해결하고, 성능과 데이터 일관성을 동시에 보장할 수 있는 방식을 구현했습니다.
최종적으로 ConcurrentHashMap과 ReentrantLock을 결합하여 ID별로 동시성을 제어하는 것이 가장 적합한 해결책임을 확인하였습니다.

##### 참고 자료
LINK : https://f-lab.kr/insight/java-spring-concurrency-control
LINK : https://parkmuhyeun.github.io/woowacourse/2023-09-09-Concurrent-Hashmap/
