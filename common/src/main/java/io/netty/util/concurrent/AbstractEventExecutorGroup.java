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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.util.concurrent.AbstractEventExecutor.*;


/**
 * EventExecutorGroup抽象实现类
 * Abstract base class for {@link EventExecutorGroup} implementations.
 */
public abstract class AbstractEventExecutorGroup implements EventExecutorGroup {
    @Override
    public Future<?> submit(Runnable task) {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交任务
        return next().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交任务
        return next().submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交任务
        return next().submit(task);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交定时任务
        return next().schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交定时任务
        return next().schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交定时任务
        return next().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交定时任务
        return next().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public Future<?> shutdownGracefully() {
        //优雅关闭
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public abstract void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交多个任务
        return next().invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交多个任务
        return next().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交多个任务，有一个任务完成即可
        return next().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        //从当前EventExecutorGroup获取其管理的一个EventExecutor提交多个任务，有一个任务完成即可
        return next().invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        next().execute(command);
    }
}
