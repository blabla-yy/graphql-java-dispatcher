package io.github.blabla;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.github.blabla.graphql.dispatcher.GraphQLEventLoopDispatcher;
import io.github.blabla.graphql.dispatcher.environment.FixedExecutorFetchingEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.SimpleStatisticsCollector;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class Main {
    private static final String SCHEMA = "type Query {" +
            " books: [Book]" +
            "}" +
            "type Book {" +
            " name: String" +
            "}";
    private static final String QUERY = "query {" +
            "books { name }" +
            "}";

    public static void main(String[] args) throws InterruptedException, TimeoutException {
        DataLoaderRegistry dataLoaderRegistry = DataLoaderRegistry.newRegistry()
                .register(CustomBatchLoader.KEY, DataLoaderFactory.newDataLoader(new CustomBatchLoader(), DataLoaderOptions.newOptions()
                                .setCachingEnabled(false) // disabled caching for testing
                                .setStatisticsCollector(SimpleStatisticsCollector::new)
                        )
                )
                .build();
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(QUERY)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build();

        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(SCHEMA);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, FETCHERS);
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema)
                // remove default instrumentation!
                .doNotAddDefaultInstrumentations()
                .build();

        ExecutionResult result = GraphQLEventLoopDispatcher.run(graphQL, input);
        System.out.println(result);
        // DataLoader was called 4 times in total
        System.out.println("dataLoader invoke count " + dataLoaderRegistry.getStatistics().getBatchInvokeCount());
    }

    private static final RuntimeWiring FETCHERS = RuntimeWiring.newRuntimeWiring()
            .type("Query", builder -> {
                return builder.dataFetcher("books", new DataFetcher<List<String>>() {
                    @Override
                    public List<String> get(DataFetchingEnvironment environment) {
                        return Arrays.asList("A", "B", "C", "D", "E", "F", "G");
                    }
                });
            })
            .type("Book", builder -> builder
                    .dataFetcher("name", new DataFetcher<CompletableFuture<String>>() {
                        @Override
                        public CompletableFuture<String> get(DataFetchingEnvironment environment) {
                            FixedExecutorFetchingEnvironment env = new FixedExecutorFetchingEnvironment(environment);
                            Thread mainThread = Thread.currentThread();
                            return env.load(CustomBatchLoader.KEY, "Hello_" + env.getSource())
                                    // using FixedExecutorFetchingEnvironment#load
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
                                        return env.switchExecutor(load);
                                    }, env.getExecutor())
                                    // or using GraphQLEventLoopDispatcher#getExecutor
                                    .thenComposeAsync(result -> {
                                        Assert.assertTrue(Thread.currentThread() == mainThread);
                                        return env.<Object, String>load(CustomBatchLoader.KEY, result);
                                    }, GraphQLEventLoopDispatcher.getExecutor(environment));
                        }
                    }))
            .build();
}