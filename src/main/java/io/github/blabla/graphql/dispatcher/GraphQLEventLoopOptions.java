package io.github.blabla.graphql.dispatcher;


public class GraphQLEventLoopOptions {
    /**
     * -1: no limit
     */
    private final int timeoutMilliseconds;
    private final boolean dispatchDataLoadersOnNextTick;
    private final Runnable nextTick;

    public int getTimeoutMilliseconds() {
        return timeoutMilliseconds;
    }

    public boolean isDispatchDataLoadersOnNextTick() {
        return dispatchDataLoadersOnNextTick;
    }

    public Runnable getNextTick() {
        return nextTick;
    }

    public GraphQLEventLoopOptions() {
        this(-1, true, null);
    }

    public GraphQLEventLoopOptions(int timeoutMilliseconds, boolean dispatchDataLoadersOnNextTick, Runnable nextTick) {
        this.timeoutMilliseconds = timeoutMilliseconds;
        this.dispatchDataLoadersOnNextTick = dispatchDataLoadersOnNextTick;
        this.nextTick = nextTick;
    }
}
