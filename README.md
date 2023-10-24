# GraphQL-Java-Dispatcher
A tool that supports GraphQL-Java asynchronous chain calls to DataLoader. Currently it is implemented based on a single-threaded event loop next-tick similar to Node.js. (Similar to graphql-js implementation)

### Features
- Supports asynchronous and chained calls of DataLoader in GraphQL-Java Fetcher.
- Single-threaded asynchronous.
- Better performance.


### Notice
1. Blocking functions should not be run in Fetcher, and time-consuming tasks can be run in components such as DataLoader.
2. Some user-defined asynchronous tasks need to be switched back to the main thread. Refer to switchExecutor in demo.
3. Not very suitable for purely asynchronous web services (multiple graphql requests may be executed concurrently and asynchronously on the same thread).

This is an intrusive implementation, and users need to be clear that all Fetchers in a graphql execution will be executed in a single thread (but excluding the running of external functions such as DataLoader).
For heavy IO applications, the single-threaded model is better. It not only saves resources, but also reduces the complexity caused by multi-threading. As for complex calculation content, it can be implemented in DataLoader or thread pool.
For the server, you can use a thread pool to run multiple GraphQL executions, such as the thread pool provided by service containers such as tomcat and jetty. No additional thread pool required!

### Demo
1. Add the dependency (Maven):
```xml
<dependency>
    <groupId>io.github.blabla-yy</groupId>
    <artifactId>graphql-java-dispatcher</artifactId>
    <version>1.0.0</version>
</dependency>
```
2. doNotAddDefaultInstrumentations()
3. Use GraphQLEventLoopDispatcher.run(graphQL, input) instead of graphQL.executeAsync(input).
```java
GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema)
        // remove default instrumentation!
        .doNotAddDefaultInstrumentations()
        .build();
ExecutionInput graphQL = ... //buildExecutionInput();
ExecutionResult result = GraphQLEventLoopDispatcher.run(graphQL, input);
```

4. All CompletableFutures in Fetcher need to specify Executor to achieve single-threaded effect. Three usage examples are given, you can choose according to the situation.
   1. It is recommended to use FixedExecutorFetchingEnvironment, which provides the wrapper function load of DataLoader.
   2. Or you can use the FixedExecutorFetchingEnvironment.switchExecutor function to switch the Executor.
   2. Or use GraphQLEventLoopDispatcher.getExecutor(environment) to get the Executor.

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
                    // using FixedExecutorFetchingEnvironment#load
                    CompletableFuture<String> future = fixedEnv.load(CustomBatchLoader.KEY, result);
                    return future;
                })
                
                // or using FixedExecutorFetchingEnvironment#switchExecutor
                .thenComposeAsync(result -> {
                    Assert.assertTrue(Thread.currentThread() == mainThread);
                    DataLoader<String, String> dataLoader = environment.getDataLoader(CustomBatchLoader.KEY);
                    CompletableFuture<String> load = dataLoader.load(CustomBatchLoader.KEY, result);
                    return fixedEnv.switchExecutor(load); // switch
                })
                
                // or using GraphQLEventLoopDispatcher#getExecutor
                .thenComposeAsync(result -> {
                    Assert.assertTrue(Thread.currentThread() == mainThread);
                    DataLoader<String, String> dataLoader = environment.getDataLoader(CustomBatchLoader.KEY);
                    CompletableFuture<String> future = dataLoader.load(result);
                }, GraphQLEventLoopDispatcher.getExecutor(environment)); // specify executor
    }
}
```