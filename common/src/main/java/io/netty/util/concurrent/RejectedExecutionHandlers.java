/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Expose helper methods which create different {@link RejectedExecutionHandler}s.
 */
public final class RejectedExecutionHandlers {
    private static final RejectedExecutionHandler REJECT = new RejectedExecutionHandler() {
        @Override
        public void rejected(Runnable task, SingleThreadEventExecutor executor) {
            //直接抛出任务注册失败的异常
            throw new RejectedExecutionException();
        }
    };

    private RejectedExecutionHandlers() { }

    /**
     * Returns a {@link RejectedExecutionHandler} that will always just throw a {@link RejectedExecutionException}.
     */
    public static RejectedExecutionHandler reject() {
        return REJECT;
    }

    /**
     * Tries to backoff when the task can not be added due restrictions for an configured amount of time. This
     * is only done if the task was added from outside of the event loop which means
     * {@link EventExecutor#inEventLoop()} returns {@code false}.
     */
    public static RejectedExecutionHandler backoff(final int retries, long backoffAmount, TimeUnit unit) {
        ObjectUtil.checkPositive(retries, "retries");
        final long backOffNanos = unit.toNanos(backoffAmount);
        return new RejectedExecutionHandler() {
            @Override
            public void rejected(Runnable task, SingleThreadEventExecutor executor) {
                //只在EventLoop里处理
                if (!executor.inEventLoop()) {
                    //再次尝试添加这个任务，直到上限
                    for (int i = 0; i < retries; i++) {
                        //唤醒执行器并执行任务
                        // Try to wake up the executor so it will empty its task queue.
                        executor.wakeup(false);
                        //在此堵塞等待
                        LockSupport.parkNanos(backOffNanos);
                        //添加新的任务
                        if (executor.offerTask(task)) {
                            return;
                        }
                    }
                }
                //不再EventLoop或者多次尝试添加失败抛出异常
                // Either we tried to add the task from within the EventLoop or we was not able to add it even with
                // backoff.
                throw new RejectedExecutionException();
            }
        };
    }
}
