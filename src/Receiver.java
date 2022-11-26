import java.util.concurrent.locks.Condition;

import java.util.concurrent.locks.Lock;

public class Receiver{
    private final Lock lock = new ReentrantLock();
    private final Condition receivedACK = lock.newCondition();
    // private final Condition checksumCorr = lock.newCondition();
    
    private final int limit = 50;
    private int count = 0;

    public int receive() throws InterruptedException {
        lock.lock();
        try {
            // retrieve received packet

            // obtain ACK in the packet

            while (count == limit) {
                // notAtLimit.await(1, TimeUnit.SECONDS);
                notAtLimit.await();
            }

            count++;
            notZero.signalAll();

            return count;
        } finally {
            lock.unlock();
        }
    }

    public int send() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notZero.await(1, TimeUnit.SECONDS);
            }

            count--;
            notAtLimit.signalAll();
            return count;
        } finally {
            lock.unlock();
        }
    }

}