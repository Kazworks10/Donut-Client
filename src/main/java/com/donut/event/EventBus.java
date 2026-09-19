package com.donut.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Minimal synchronous event bus with async support.
 * <p>
 * Subscriptions are returned as cancellable handles so modules can unsubscribe
 * cleanly on disable. Sync dispatch is intended for the client thread; async
 * dispatch runs listeners on a virtual-thread pool and never blocks the caller.
 */
public final class EventBus {
    private final Map<Class<?>, List<Subscription<?>>> listeners = new ConcurrentHashMap<>();
    private final ExecutorService asyncPool = Executors.newVirtualThreadPerTaskExecutor();

    public <T> Subscription<T> subscribe(Class<T> type, Consumer<T> handler) {
        Subscription<T> sub = new Subscription<>(type, handler);
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(sub);
        return sub;
    }

    public <T> void unsubscribe(Subscription<T> sub) {
        List<Subscription<?>> list = listeners.get(sub.type);
        if (list != null) list.remove(sub);
    }

    public <T> void post(T event) {
        List<Subscription<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) return;
        for (Subscription<?> sub : list) {
            invoke(sub, event);
        }
    }

    /** Fire-and-forget dispatch on a virtual thread. Handler errors are swallowed and logged. */
    public <T> void postAsync(T event) {
        List<Subscription<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) return;
        // Snapshot under lock-free iteration; the pool dispatches a stable view.
        List<Subscription<?>> snapshot = List.copyOf(list);
        asyncPool.submit(() -> {
            for (Subscription<?> sub : snapshot) invoke(sub, event);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void invoke(Subscription<?> sub, T event) {
        try {
            ((Consumer<T>) sub.handler).accept(event);
        } catch (Throwable t) {
            System.err.println("[Donut] Event handler error for " + sub.type.getSimpleName() + ": " + t);
        }
    }

    public static final class Subscription<T> {
        final Class<T> type;
        final Consumer<T> handler;

        Subscription(Class<T> type, Consumer<T> handler) {
            this.type = type;
            this.handler = handler;
        }
    }
}
