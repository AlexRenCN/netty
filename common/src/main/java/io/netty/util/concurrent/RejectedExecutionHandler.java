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

/**
 * 拒绝任务执行器
 * Similar to {@link java.util.concurrent.RejectedExecutionHandler} but specific to {@link SingleThreadEventExecutor}.
 */
public interface RejectedExecutionHandler {

    /**
     * 当试图在简单任务执行器中添加任务，但是容量不够失败的时候调用
     * Called when someone tried to add a task to {@link SingleThreadEventExecutor} but this failed due capacity
     * restrictions.
     */
    void rejected(Runnable task, SingleThreadEventExecutor executor);
}
