package io.github.blabla;

import org.dataloader.BatchLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CustomBatchLoader implements BatchLoader<String, String> {
    public static final String KEY = "CustomBatchLoader";
    @Override
    public CompletionStage<List<String>> load(List<String> keys) {
        // using ForkJoinPool
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(2 * 100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return keys;
        });
    }
}
