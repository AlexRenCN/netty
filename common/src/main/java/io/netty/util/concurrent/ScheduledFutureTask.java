/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util.concurrent;

import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueueNode;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
final class ScheduledFutureTask<V> extends PromiseTask<V> implements ScheduledFuture<V>, PriorityQueueNode {
    /**
     * 任务序号发号器
     */
    private static final AtomicLong nextTaskId = new AtomicLong();
    /**
     * 定时任务起始时间
     */
    private static final long START_TIME = System.nanoTime();

    /**
     * 距离执行的时间还有多久
     * @return
     */
    static long nanoTime() {
        //相对于起始时间的差距
        return System.nanoTime() - START_TIME;
    }

    /**
     * 延迟执行
     * @param delay
     * @return
     */
    static long deadlineNanos(long delay) {
        long deadlineNanos = nanoTime() + delay;
        // Guard against overflow
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    static long initialNanoTime() {
        return START_TIME;
    }


    /**
     * 任务编号,递增
     */
    private final long id = nextTaskId.getAndIncrement();
    /**
     * 任务到期时间
     */
    private long deadlineNanos;
    /**
     * 任务执行周期
     * 0不重复
     * >0以固定的时间重复
     * <0以实际的实际重复
     * 固定时间和实际时间的区别就是定时任务执行的时间需不需要算进去
     */
    /* 0 - no repeat, >0 - repeat at fixed rate, <0 - repeat with fixed delay */
    private final long periodNanos;

    private int queueIndex = INDEX_NOT_IN_QUEUE;

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Runnable runnable, V result, long nanoTime) {

        this(executor, toCallable(runnable, result), nanoTime);
    }

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime, long period) {

        super(executor, callable);
        if (period == 0) {
            throw new IllegalArgumentException("period: 0 (expected: != 0)");
        }
        deadlineNanos = nanoTime;
        periodNanos = period;
    }

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime) {

        super(executor, callable);
        deadlineNanos = nanoTime;
        periodNanos = 0;
    }

    @Override
    protected EventExecutor executor() {
        return super.executor();
    }

    public long deadlineNanos() {
        return deadlineNanos;
    }

    public long delayNanos() {
        return deadlineToDelayNanos(deadlineNanos());
    }

    /**
     * 距离当前时间，还有多久才要执行
     * @param deadlineNanos
     * @return
     */
    static long deadlineToDelayNanos(long deadlineNanos) {
        return Math.max(0, deadlineNanos - nanoTime());
    }

    /**
     * 距离指定时间还有多久执行
     * @param currentTimeNanos
     * @return
     */
    public long delayNanos(long currentTimeNanos) {
        return Math.max(0, deadlineNanos() - (currentTimeNanos - START_TIME));
    }

    /**
     * 获取到期时间并转换时间单位
     * @param unit
     * @return
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o) {
            return 0;
        }

        ScheduledFutureTask<?> that = (ScheduledFutureTask<?>) o;
        //使用两个任务的到期时间作为排序项
        long d = deadlineNanos() - that.deadlineNanos();
        if (d < 0) {
            return -1;
        } else if (d > 0) {
            return 1;
        } else if (id < that.id) {
            return -1;
        } else {
            assert id != that.id;
            return 1;
        }
    }

    @Override
    public void run() {
        assert executor().inEventLoop();
        try {
            //如果是只执行一次的任务
            if (periodNanos == 0) {
                //设置任务不可取消
                if (setUncancellableInternal()) {
                    //执行任务
                    V result = task.call();
                    //设置任务为执行成果
                    setSuccessInternal(result);
                }
            } else {
                //定时的任务，检查是不是已经被取消了
                // check if is done as it may was cancelled
                if (!isCancelled()) {
                    //如果没有取消就执行任务
                    task.call();
                    //如果执行器没有关闭
                    if (!executor().isShutdown()) {
                        //设置下次执行时间
                        if (periodNanos > 0) {
                            //固定时间，上次时间+间隔
                            deadlineNanos += periodNanos;
                        } else {
                            //实际时间，当前时间+间隔
                            deadlineNanos = nanoTime() - periodNanos;
                        }
                        //如果任务没有取消
                        if (!isCancelled()) {
                            //添加到任务队列中等待下次执行
                            // scheduledTaskQueue can never be null as we lazy init it before submit the task!
                            Queue<ScheduledFutureTask<?>> scheduledTaskQueue =
                                    ((AbstractScheduledEventExecutor) executor()).scheduledTaskQueue;
                            assert scheduledTaskQueue != null;
                            scheduledTaskQueue.add(this);
                        }
                    }
                }
            }
        } catch (Throwable cause) {
            //设置为执行失败
            setFailureInternal(cause);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        //移除任务
        boolean canceled = super.cancel(mayInterruptIfRunning);
        //如果移除成功
        if (canceled) {
            //从任务队列中移除
            ((AbstractScheduledEventExecutor) executor()).removeScheduled(this);
        }
        return canceled;
    }

    boolean cancelWithoutRemove(boolean mayInterruptIfRunning) {
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = super.toStringBuilder();
        buf.setCharAt(buf.length() - 1, ',');

        return buf.append(" id: ")
                  .append(id)
                  .append(", deadline: ")
                  .append(deadlineNanos)
                  .append(", period: ")
                  .append(periodNanos)
                  .append(')');
    }

    @Override
    public int priorityQueueIndex(DefaultPriorityQueue<?> queue) {
        return queueIndex;
    }

    @Override
    public void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i) {
        queueIndex = i;
    }
}
