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

import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.UnstableApi;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认EventExecutor选择器，使用最简单的轮询方式
 * Default implementation which uses simple round-robin to choose next {@link EventExecutor}.
 */
@UnstableApi
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() { }

    @SuppressWarnings("unchecked")
    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        //如果处理器的个数是2的幂
        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            //如果处理器的个数不是2的幂
            return new GenericEventExecutorChooser(executors);
        }
    }

    /**
     * 是否是2的幂
     * @param val
     * @return
     */
    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        /**
         * 自增序列，用来计算需要获取的处理器下标
         */
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            // 使用自增序列和处理器个数计算需要获取的处理器下标
            // 这是一个比较巧妙的方法，用于处理器的个数是2的幂场景下，此时executors.length - 1结果一定是最高位为0，其余为数为1，
            // 用递增序列进行与运算得出的数不会超过executors.length且保持循环和递增。在int溢出后变成Integer.MIN_VALUE,还能保持循环和递增趋势
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }


    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        /**
         * 自增序列，用来计算需要获取的处理器下标
         */
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            //使用自增序列和处理器个数计算需要获取的处理器下标
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}
