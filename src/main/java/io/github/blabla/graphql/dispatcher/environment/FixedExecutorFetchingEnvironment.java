package io.github.blabla.graphql.dispatcher.environment;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DelegatingDataFetchingEnvironment;
import io.github.blabla.executor.AsyncHelper;
import io.github.blabla.executor.CurrentThreadExecutor;
import io.github.blabla.graphql.dispatcher.GraphQLEventLoopDispatcher;
import org.dataloader.DataLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * A tool to help fix Executor for all Futures in Fetcher.
 * If you are just using DataLoader, you can use the load function.
 * If it is another Future, you can use checkExecutor to convert the thread of CompletableFuture.
 * </p>
 * Executor:
 *
 * @see io.github.blabla.graphql.dispatcher.GraphQLEventLoopDispatcher
 */
public class FixedExecutorFetchingEnvironment extends DelegatingDataFetchingEnvironment implements DataFetchingEnvironment {
    private final Executor executor;

    public Executor getExecutor() {
        return executor;
    }

    public FixedExecutorFetchingEnvironment(DataFetchingEnvironment delegateEnvironment, Executor executor) {
        super(delegateEnvironment);
        if (executor == null) {
            throw new NullPointerException();
        }
        this.executor = executor;
    }

    /**
     * default GraphQLEventLoopDispatcher dispatcher
     */
    public FixedExecutorFetchingEnvironment(DataFetchingEnvironment delegateEnvironment) {
        super(delegateEnvironment);
        Executor executor = GraphQLEventLoopDispatcher.getExecutor(this.getGraphQlContext());
        if (executor == null) {
            throw new NullPointerException();
        }
        this.executor = executor;
    }

    /**
     * Check the future status and whether the thread is an Executor thread to avoid unnecessary processing
     */
    public <T> CompletableFuture<T> switchExecutor(CompletableFuture<T> future) {
        if (!(this.executor instanceof CurrentThreadExecutor)) {
            return future;
        }
        CurrentThreadExecutor executor = (CurrentThreadExecutor) this.executor;
        if (AsyncHelper.isCompleted(future) && executor.isWorkerThread()) {
            return future;
        }
        return future.thenApplyAsync(Function.identity(), executor);
    }

    public <K, V> CompletableFuture<V> load(String dataLoaderName, K param) {
        DataLoader<K, V> dataLoader = this.getDataLoader(dataLoaderName);
        return this.load(dataLoader, param);
    }

    // basic
    public <K, V> CompletableFuture<V> load(DataLoader<K, V> dataLoader, K param) {
        return this.switchExecutor(dataLoader.load(param));
    }

    public <K, V> CompletableFuture<V> load(String dataLoaderName, K param, Object context) {
        DataLoader<K, V> dataLoader = this.getDataLoader(dataLoaderName);
        return this.load(dataLoader, param, context);
    }

    // basic
    public <K, V> CompletableFuture<V> load(DataLoader<K, V> dataLoader, K param, Object context) {
        return this.switchExecutor(dataLoader.load(param, context));
    }

    public <K, V> CompletableFuture<List<V>> loadMany(String dataLoaderName, List<K> params) {
        DataLoader<K, V> dataLoader = this.getDataLoader(dataLoaderName);
        return this.loadMany(dataLoader, params);
    }

    // basic
    public <K, V> CompletableFuture<List<V>> loadMany(DataLoader<K, V> dataLoader, List<K> params) {
        return this.switchExecutor(dataLoader.loadMany(params));
    }

    public <K, V> CompletableFuture<List<V>> loadMany(String dataLoaderName, List<K> params, List<Object> contexts) {
        DataLoader<K, V> dataLoader = this.getDataLoader(dataLoaderName);
        return this.loadMany(dataLoader, params, contexts);
    }

    // basic
    public <K, V> CompletableFuture<List<V>> loadMany(DataLoader<K, V> dataLoader, List<K> params, List<Object> contexts) {
        return this.switchExecutor(dataLoader.loadMany(params, contexts));
    }
}
