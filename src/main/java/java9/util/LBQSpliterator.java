/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package java9.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import java9.util.function.Consumer;

/**
 * A customized variant of Spliterators.IteratorSpliterator for
 * LinkedBlockingQueues.
 * Keep this class in sync with (very similar) LBDSpliterator.
 * <p>
 * The returned spliterator is <i>weakly consistent</i>.
 * <p>
 * The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
 * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
 * <p>
 * The {@code Spliterator} implements {@code trySplit} to permit limited
 * parallelism.
 * 
 * @param <E> the type of elements held in the LinkedBlockingQueue
 */
final class LBQSpliterator<E> implements Spliterator<E> {
// CVS rev. 1.114
    private static final int MAX_BATCH = 1 << 25; // max batch array size
    private final LinkedBlockingQueue<E> queue;
    private final ReentrantLock putLock;
    private final ReentrantLock takeLock;
    private Object current; // current node; null until initialized
    private int batch; // batch size for splits
    private boolean exhausted; // true when no more nodes
    private long est; // size estimate

    private LBQSpliterator(LinkedBlockingQueue<E> queue) {
        this.queue = queue;
        this.est = queue.size();
        this.putLock = getPutLock(queue);
        this.takeLock = getTakeLock(queue);
    }

    static <T> Spliterator<T> spliterator(LinkedBlockingQueue<T> queue) {
        return new LBQSpliterator<T>(queue);
    }

    Object succ(Object p) {
        return (p == (p = getNextNode(p))) ? getHeadNext(queue) : p;
    }

    @Override
    public int characteristics() {
        return (Spliterator.ORDERED |
                Spliterator.NONNULL |
                Spliterator.CONCURRENT);
    }

    @Override
    public long estimateSize() {
        return est;
    }

    @Override
    public void forEachRemaining(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        if (!exhausted) {
            exhausted = true;
            Object p = current;
            current = null;
            forEachFrom(action, p);
        }
    }

    @Override
    public boolean tryAdvance(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        if (!exhausted) {
            E e = null;
            fullyLock();
            try {
                Object p;
                if ((p = current) != null || (p = getHeadNext(queue)) != null)
                    do {
                        e = getNodeItem(p);
                        p = succ(p);
                    } while (e == null && p != null);
                if ((current = p) == null)
                    exhausted = true;
            } finally {
                fullyUnlock();
            }
            if (e != null) {
                action.accept(e);
                return true;
            }
        }
        return false;
    }

    @Override
    public Spliterator<E> trySplit() {
        Object h;
        LinkedBlockingQueue<E> q = queue;
        if (!exhausted &&
            ((h = current) != null || (h = getHeadNext(q)) != null)
            && getNextNode(h) != null) {
            int n = batch = Math.min(batch + 1, MAX_BATCH);
            Object[] a = new Object[n];
            int i = 0;
            Object p = current;
            fullyLock();
            try {
                if (p != null || (p = getHeadNext(q)) != null)
                    for (; p != null && i < n; p = succ(p))
                        if ((a[i] = getNodeItem(p)) != null)
                            i++;
            } finally {
                fullyUnlock();
            }
            if ((current = p) == null) {
                est = 0L;
                exhausted = true;
            }
            else if ((est -= i) < 0L)
                est = 0L;
            if (i > 0)
                return Spliterators.spliterator
                    (a, 0, i, (Spliterator.ORDERED |
                               Spliterator.NONNULL |
                               Spliterator.CONCURRENT));
        }
        return null;
    }

    /**
     * Runs action on each element found during a traversal starting at p.
     * If p is null, traversal starts at head.
     */
    void forEachFrom(Consumer<? super E> action, Object p) {
        // Extract batches of elements while holding the lock; then
        // run the action on the elements while not
        final int batchSize = 64;       // max number of elements per batch
        Object[] es = null;             // container for batch of elements
        int n, len = 0;
        do {
            fullyLock();
            try {
                if (es == null) {
                    if (p == null) p = getHeadNext(queue);
                    for (Object q = p; q != null; q = succ(q))
                        if (getNodeItem(q) != null && ++len == batchSize)
                            break;
                    es = new Object[len];
                }
                for (n = 0; p != null && n < len; p = succ(p))
                    if ((es[n] = getNodeItem(p)) != null)
                        n++;
            } finally {
                fullyUnlock();
            }
            for (int i = 0; i < n; i++) {
                @SuppressWarnings("unchecked") E e = (E) es[i];
                action.accept(e);
            }
        } while (n > 0 && p != null);
    }

    /**
     * Lock to prevent both puts and takes.
     */
    private void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /**
     * Unlock to allow both puts and takes.
     */
    private void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

    private static ReentrantLock getPutLock(LinkedBlockingQueue<?> queue) {
        return (ReentrantLock) U.getObject(queue, PUT_LOCK_OFF);
    }

    private static ReentrantLock getTakeLock(LinkedBlockingQueue<?> queue) {
        return (ReentrantLock) U.getObject(queue, TAKE_LOCK_OFF);
    }

    /**
     * Returns queue.head.next as an Object
     */
    private static <T> Object getHeadNext(LinkedBlockingQueue<T> queue) {
        return getNextNode(U.getObject(queue, HEAD_OFF));
    }

    /**
     * Returns node.next as an Object
     */
    private static Object getNextNode(Object node) {
        return U.getObject(node, NODE_NEXT_OFF);
    }

    /**
     * Returns node.item as a T
     */
    private static <T> T getNodeItem(Object node) {
        return (T) U.getObject(node, NODE_ITEM_OFF);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U = UnsafeAccess.unsafe;
    private static final long HEAD_OFF;
    private static final long NODE_ITEM_OFF;
    private static final long NODE_NEXT_OFF;
    private static final long PUT_LOCK_OFF;
    private static final long TAKE_LOCK_OFF;
    static {
        try {
            Class<?> nc = Class
                    .forName("java.util.concurrent.LinkedBlockingQueue$Node");
            HEAD_OFF = U.objectFieldOffset(LinkedBlockingQueue.class
                    .getDeclaredField("head"));
            NODE_ITEM_OFF = U.objectFieldOffset(nc.getDeclaredField("item"));
            NODE_NEXT_OFF = U.objectFieldOffset(nc.getDeclaredField("next"));
            PUT_LOCK_OFF = U.objectFieldOffset(LinkedBlockingQueue.class
                    .getDeclaredField("putLock"));
            TAKE_LOCK_OFF = U.objectFieldOffset(LinkedBlockingQueue.class
                    .getDeclaredField("takeLock"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
