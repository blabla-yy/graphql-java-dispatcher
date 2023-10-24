# GraphQL-Java-Dispatcher
A tool that supports GraphQL-Java asynchronous chain calls to DataLoader. Currently GraphQLEventLoopDispatcher is implemented based on a single-threaded event loop next-tick similar to Node.js. (Similar to graphql-js implementation)

This is an intrusive implementation, and users need to be clear that all Fetchers will be executed in a single thread (but excluding the running of external functions such as DataLoader).
For heavy IO applications, the single-threaded model is better. It not only saves resources, but also reduces the complexity caused by multi-threading. As for complex calculation content, it can be implemented in DataLoader or thread pool.


### Features
- Supports asynchronous and chained calls of DataLoader in GraphQL-Java Fetcher.
- Better performance.


### Notice
1. Blocking functions should not be run in Fetcher, and time-consuming tasks can be run in components such as DataLoader.
2. Other components that may affect the Fetcher execution thread, such as instrumentation, also need to specify the Executor to ensure single threading.

### Demo
1. doNotAddDefaultInstrumentations()
2. Use GraphQLEventLoopDispatcher.run(graphQL, input) instead of graphQL.executeAsync(input).
```java
GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema)
        // remove default instrumentation!
        .doNotAddDefaultInstrumentations()
        .build();
ExecutionInput graphQL = buildExecutionInput();
ExecutionResult result = GraphQLEventLoopDispatcher.run(graphQL, input);
```

2. All CompletableFutures in Fetcher need to specify Executor to achieve single-threaded effect. Three usage examples are given, you can choose according to the situation.
   1. It is recommended to use FixedExecutorFetchingEnvironment, which provides the wrapper function load of DataLoader and the function switchExecutor to switch Executor.
   2. Use GraphQLEventLoopDispatcher.getExecutor(environment) to get the Executor.

```java
DataFetcher<CompletableFuture<String>> dataFetcher = new DataFetcher<CompletableFuture<String>>() {
    @Override
    public CompletableFuture<String> get(DataFetchingEnvironment environment) {
        // using FixedExecutorFetchingEnvironment#load
        FixedExecutorFetchingEnvironment fixedEnv = new FixedExecutorFetchingEnvironment(environment);
        Thread mainThread = Thread.currentThread();
            return fixedEnv.load(CustomBatchLoader.KEY, "Hello_" + fixedEnv.getSource())
                .thenCompose(result -> {
                    Assert.assertTrue(Thread.currentThread() == mainThread);
                    CompletableFuture<String> future = env.load(CustomBatchLoader.KEY, result);
                    return future;
                })
                
                // or using FixedExecutorFetchingEnvironment#switchExecutor
                .thenComposeAsync(result -> {
                    Assert.assertTrue(Thread.currentThread() == mainThread);
                    DataLoader<String, String> dataLoader = environment.getDataLoader(CustomBatchLoader.KEY);
                    CompletableFuture<String> load = dataLoader.load(CustomBatchLoader.KEY, result);
                    return fixedEnv.switchExecutor(load);
                })
                
                // or using GraphQLEventLoopDispatcher#getExecutor
                .thenComposeAsync(result -> {
                    Assert.assertTrue(Thread.currentThread() == mainThread);
                    DataLoader<String, String> dataLoader = environment.getDataLoader(CustomBatchLoader.KEY);
                    CompletableFuture<String> future = dataLoader.load(result);
                }, GraphQLEventLoopDispatcher.getExecutor(environment));
    }
}
```