/*
 * Copyright 2012 The Netty Project
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

package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 * <p>When not used in a {@link ServerBootstrap} context, the {@link #bind()} methods are useful for connectionless
 * transports such as datagram (UDP).</p>
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {

    /**
     * netty调度模块
     */
    volatile EventLoopGroup group;
    /**
     * channel工厂
     */
    @SuppressWarnings("deprecation")
    private volatile ChannelFactory<? extends C> channelFactory;
    /**
     * 本地地址
     */
    private volatile SocketAddress localAddress;
    /**
     * 新建channel用的可选项
     */
    private final Map<ChannelOption<?>, Object> options = new ConcurrentHashMap<ChannelOption<?>, Object>();
    /**
     * 新建channel用的属性
     */
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<AttributeKey<?>, Object>();
    /**
     * 新建channel用的处理器
     */
    private volatile ChannelHandler handler;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        options.putAll(bootstrap.options);
        attrs.putAll(bootstrap.attrs);
    }

    /**
     * 设置处理所有事件，创建channel的EventLoopGroup
     * 是一种单Reactor单/多线程模型（取决于有多少业务）
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-created
     * {@link Channel}
     */
    public B group(EventLoopGroup group) {
        ObjectUtil.checkNotNull(group, "group");
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }

    /**
     * 返回自己
     * @return
     */
    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**
     * 设置被实例化的Channel类型
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(io.netty.channel.ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public B channel(Class<? extends C> channelClass) {
        return channelFactory(new ReflectiveChannelFactory<C>(
                ObjectUtil.checkNotNull(channelClass, "channelClass")
        ));
    }

    /**
     * @deprecated Use {@link #channelFactory(io.netty.channel.ChannelFactory)} instead.
     */
    @Deprecated
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        ObjectUtil.checkNotNull(channelFactory, "channelFactory");
        //channel工厂不可以为空
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * 用于创建channel的工厂
     * {@link io.netty.channel.ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} to
     * simplify your code.
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public B channelFactory(io.netty.channel.ChannelFactory<? extends C> channelFactory) {
        return channelFactory((ChannelFactory<C>) channelFactory);
    }

    /**
     * 设置channel的本地地址
     * The {@link SocketAddress} which is used to bind the local "end" to.
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    /**
     * 设置channel的本地地址
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    /**
     * 设置channel的本地地址
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * 设置channel的本地地址
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * 设置创建Channel的可选项
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> B option(ChannelOption<T> option, T value) {
        ObjectUtil.checkNotNull(option, "option");
        if (value == null) {
            //value为空移除
            options.remove(option);
        } else {
            //value不为空进行设置
            options.put(option, value);
        }
        return self();
    }

    /**
     * 设置创建channel的属性
     * Allow to specify an initial attribute of the newly created {@link Channel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        ObjectUtil.checkNotNull(key, "key");
        if (value == null) {
            //value为空，移除
            attrs.remove(key);
        } else {
            //value不为空进行设置
            attrs.put(key, value);
        }
        return self();
    }

    /**
     * 检验配置是否正确
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     */
    public B validate() {
        //调度模块不可以为空
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        //channel工厂不可以为空
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    /**
     * 返回一个深克隆的启动器
     * Returns a deep clone of this bootstrap which has the identical configuration.  This method is useful when making
     * multiple {@link Channel}s with similar settings.  Please note that this method does not clone the
     * {@link EventLoopGroup} deeply but shallowly, making the group a shared resource.
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * 创建一个channel并绑定在事件处理器上
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * 绑定本地端口
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        //校验配置
        validate();
        //获取本地端口
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        //绑定本地端口
        return doBind(localAddress);
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        //校验配置
        validate();
        //绑定本地端口
        return doBind(ObjectUtil.checkNotNull(localAddress, "localAddress"));
    }

    private ChannelFuture doBind(final SocketAddress localAddress) {
        //初始化并注册一个channel，该步骤是异步的
        final ChannelFuture regFuture = initAndRegister();
        //获取注册的channel
        final Channel channel = regFuture.channel();
        //如果发生异常，直接返回
        if (regFuture.cause() != null) {
            return regFuture;
        }

        //如果注册任务已经完成
        if (regFuture.isDone()) {
            // At this point we know that the registration was complete and successful.

            //获取一个特殊的可写的ChannelFuture
            ChannelPromise promise = channel.newPromise();
            //实际绑定
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            //注册任务未完成
            // Registration future is almost always fulfilled already, but just in case it's not.
            //如果异步注册操作没有完成，新建一个Promise
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    //Future关联完成时，检查是否发生异常
                    Throwable cause = future.cause();
                    if (cause != null) {
                        //注册发生异常，一旦尝试访问调度模块，就会直接失败，不会导致IllegalStateException
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        //注册成功，设置注册状态为true
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        //真实绑定
                        doBind0(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }

    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            //创建一个新的通道
            channel = channelFactory.newChannel();
            //初始化通道
            init(channel);
        } catch (Throwable t) {
            //如果通道已经创建
            if (channel != null) {
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                //强制关闭通道
                channel.unsafe().closeForcibly();
                // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now because the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        return regFuture;
    }

    abstract void init(Channel channel) throws Exception;

    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                //绑定成功
                if (regFuture.isSuccess()) {
                    //绑定端口
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    //将异常绑定
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    /**
     * 新建channel的处理器
     * the {@link ChannelHandler} to use for serving the requests.
     */
    public B handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return self();
    }

    /**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     *
     * @deprecated Use {@link #config()} instead.
     */
    @Deprecated
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * 返回当前启动器的配置对象
     * Returns the {@link AbstractBootstrapConfig} object that can be used to obtain the current config
     * of the bootstrap.
     */
    public abstract AbstractBootstrapConfig<B, C> config();

    final Map<ChannelOption<?>, Object> options0() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs0() {
        return attrs;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    @SuppressWarnings("deprecation")
    final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    final Map<ChannelOption<?>, Object> options() {
        return copiedMap(options);
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return copiedMap(attrs);
    }

    static <K, V> Map<K, V> copiedMap(Map<K, V> map) {
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<K, V>(map));
    }

    /**
     * 设置传入channel的多个属性
     * @param channel
     * @param attrs
     */
    static void setAttributes(Channel channel, Map.Entry<AttributeKey<?>, Object>[] attrs) {
        for (Map.Entry<AttributeKey<?>, Object> e: attrs) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
            channel.attr(key).set(e.getValue());
        }
    }

    /**
     * 设置传入channel的多个可选项
     * @param channel
     * @param options
     * @param logger
     */
    static void setChannelOptions(
            Channel channel, Map.Entry<ChannelOption<?>, Object>[] options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    @SuppressWarnings("unchecked")
    static Map.Entry<AttributeKey<?>, Object>[] newAttrArray(int size) {
        return new Map.Entry[size];
    }

    @SuppressWarnings("unchecked")
    static Map.Entry<ChannelOption<?>, Object>[] newOptionArray(int size) {
        return new Map.Entry[size];
    }

    @SuppressWarnings("unchecked")
    private static void setChannelOption(
            Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('(').append(config()).append(')');
        return buf.toString();
    }

    static final class PendingRegistrationPromise extends DefaultChannelPromise {

        // Is set to the correct EventExecutor once the registration was successful. Otherwise it will
        // stay null and so the GlobalEventExecutor.INSTANCE will be used for notifications.
        private volatile boolean registered;

        PendingRegistrationPromise(Channel channel) {
            super(channel);
        }

        void registered() {
            registered = true;
        }

        @Override
        protected EventExecutor executor() {
            if (registered) {
                // If the registration was a success executor is set.
                //
                // See https://github.com/netty/netty/issues/2586
                return super.executor();
            }
            // The registration failed so we can only use the GlobalEventExecutor as last resort to notify.
            return GlobalEventExecutor.INSTANCE;
        }
    }
}
