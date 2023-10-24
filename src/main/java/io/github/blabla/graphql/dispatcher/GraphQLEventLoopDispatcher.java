package io.github.blabla.graphql.dispatcher;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.github.blabla.executor.CurrentThreadExecutor;
import io.github.blabla.executor.Status;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 使用类似Node.js单线程事件循环的Next-tick机制触发DataLoader请求。
 * 解决原生GraphQL-Java中无法异步环境下触发DataLoader问题。
 * 这是一个侵入性较强的Dispatcher，需要单线程处理所有GraphQL Fetcher任务（DataLoader等部分可以是异步或多线程），
 * 所以需要在Fetcher中的Future指定Executor，可以借助FixedExecutorFetchingEnvironment实现。
 * </p>
 * Use the Next-tick mechanism similar to the Node.js single-threaded event loop to trigger DataLoader requests.
 * Solve the problem that DataLoader cannot be triggered in an asynchronous environment in native GraphQL-Java.
 * This is a highly intrusive Dispatcher that requires a single thread to process all GraphQL Fetcher tasks
 * (DataLoader and other parts can be asynchronous or multi-threaded),
 * so the Executor needs to be specified in the futures of Fetcher,
 * which can be achieved with the help of FixedExecutorFetchingEnvironment.
 */
public class GraphQLEventLoopDispatcher {
    private static final GraphQLEventLoopOptions DEFAULT = new GraphQLEventLoopOptions();

    public static Executor getExecutor(GraphQLContext graphQLContext) {
        if (graphQLContext == null) {
            throw new NullPointerException();
        }
        return graphQLContext.get(GraphQLEventLoopDispatcher.class);
    }

    public static Executor getExecutor(DataFetchingEnvironment environment) {
        if (environment == null) {
            throw new NullPointerException();
        }
        return getExecutor(environment.getGraphQlContext());
    }

    public static ExecutionResult run(GraphQL graphQL, ExecutionInput input) throws InterruptedException, TimeoutException {
        return GraphQLEventLoopDispatcher.run(graphQL, input, DEFAULT);
    }

    public static ExecutionResult run(GraphQL graphQL, ExecutionInput input,
                                      GraphQLEventLoopOptions options) throws InterruptedException, TimeoutException {
        if (graphQL == null || input == null || options == null) {
            throw new NullPointerException();
        }
        if (input.getGraphQLContext().get(GraphQLEventLoopDispatcher.class) != null) {
            throw new RuntimeException("GraphQLEventLoopDispatcher context has been set");
        }
        // setup executor
        CurrentThreadExecutor executor = GraphQLEventLoopDispatcher.createCurrentThreadExecutor(input, options);
        input.getGraphQLContext().put(GraphQLEventLoopDispatcher.class, executor);

        // execute query
        ExecutionResult executionResult = executor.start(() -> graphQL.executeAsync(input), options.getTimeoutMilliseconds(), TimeUnit.MILLISECONDS);
        if (executionResult == null && executor.getStatus() == Status.TIMEOUT) {
            throw new TimeoutException("graphql execution timeout");
        }
        return executionResult;
    }

    private static CurrentThreadExecutor createCurrentThreadExecutor(ExecutionInput input, GraphQLEventLoopOptions options) {
        CurrentThreadExecutor executor;
        if (options.isDispatchDataLoadersOnNextTick() || options.getNextTick() != null) {
            executor = new CurrentThreadExecutor(() -> {
                if (options.isDispatchDataLoadersOnNextTick()) {
                    input.getDataLoaderRegistry().dispatchAll();
                }
                if (options.getNextTick() != null) {
                    options.getNextTick().run();
                }
            });
        } else {
            executor = new CurrentThreadExecutor();
        }
        return executor;
    }
}
