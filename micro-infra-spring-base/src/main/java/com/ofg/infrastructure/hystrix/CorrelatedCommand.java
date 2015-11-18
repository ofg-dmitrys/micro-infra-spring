package com.ofg.infrastructure.hystrix;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.instrument.TraceCallable;
import org.springframework.cloud.sleuth.instrument.circuitbreaker.TraceCommand;

import java.util.concurrent.Callable;

public abstract class CorrelatedCommand<R> extends TraceCommand<R> {

    private final Trace trace;
    private final Span storedSpan;

    protected CorrelatedCommand(Trace trace, HystrixCommandGroupKey group) {
        super(trace, group);
        this.trace = trace;
        storedSpan = TraceContextHolder.getCurrentSpan();
    }

    protected CorrelatedCommand(Trace trace, HystrixCommandGroupKey group, HystrixThreadPoolKey threadPool) {
        super(trace, group, threadPool);
        this.trace = trace;
        storedSpan = TraceContextHolder.getCurrentSpan();
    }

    protected CorrelatedCommand(Trace trace, HystrixCommandGroupKey group, int executionIsolationThreadTimeoutInMilliseconds) {
        super(trace, group, executionIsolationThreadTimeoutInMilliseconds);
        this.trace = trace;
        storedSpan = TraceContextHolder.getCurrentSpan();
    }

    protected CorrelatedCommand(Trace trace, HystrixCommandGroupKey group, HystrixThreadPoolKey threadPool, int executionIsolationThreadTimeoutInMilliseconds) {
        super(trace, group, threadPool, executionIsolationThreadTimeoutInMilliseconds);
        this.trace = trace;
        storedSpan = TraceContextHolder.getCurrentSpan();
    }

    protected CorrelatedCommand(Trace trace, Setter setter) {
        super(trace, setter);
        this.trace = trace;
        storedSpan = TraceContextHolder.getCurrentSpan();
    }

    @Override
    protected R run() throws Exception {
        TraceScope scope = trace.startSpan(getCommandKey().name());
        try {
            return new TraceCallable<>(trace, new Callable<R>() {
                @Override
                public R call() throws Exception {
                    return doRun();
                }
            }, storedSpan).call();
        } finally {
            scope.close();
        }
    }

}
