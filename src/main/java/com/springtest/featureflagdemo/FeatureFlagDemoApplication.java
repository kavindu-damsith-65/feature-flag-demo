package com.springtest.featureflagdemo;

import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@SpringBootApplication
public class FeatureFlagDemoApplication {

    private static final String FLAG_KEY = "show-welcome-banner";
    private static final int WORKERS = 4000;
    private static final int ROUNDS = 5;

    public static void main(String[] args) {
        SpringApplication.run(FeatureFlagDemoApplication.class, args);
    }

    @Bean
    public FlagdProvider flagdProvider() {
        FlagdOptions options = FlagdOptions.builder()
                .host("quat-feature.tools.flagd")
                .port(8013)
                .tls(true)
                .deadline(1000)
//                .cacheType("disabled")
//                .maxCacheSize(0)  // No cache entries
                .build();
        return new FlagdProvider(options);
    }

    @Bean
    public OpenFeatureAPI openFeatureAPI(FlagdProvider flagdProvider) {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProvider(flagdProvider);
        return api;
    }

    @Bean
    public Client featureFlagClient(OpenFeatureAPI api) {
        return api.getClient("feature-flag-demo-client");
    }

    @Bean
    public CommandLineRunner runner(Client client) {
        return args -> {
            ExecutorService executor = Executors.newFixedThreadPool(100);

            long totalStart = System.currentTimeMillis();

            for (int round = 1; round <= ROUNDS; round++) {
                System.out.println("\n--- Round " + round + " ---");
                long roundStart = System.currentTimeMillis();

                List<Callable<Void>> tasks = new ArrayList<>();
                for (int i = 1; i <= WORKERS; i++) {
                    final int workerId = i;
                    tasks.add(() -> {
                        boolean value = client.getBooleanValue(FLAG_KEY, false);
                        String variant = client.getBooleanDetails(FLAG_KEY, false).getVariant();
                        System.out.printf("[Worker %d] Value: %b, Variant: %s%n", workerId, value, variant);
                        return null;
                    });
                }

                executor.invokeAll(tasks);
                long roundEnd = System.currentTimeMillis();
                System.out.println("Round " + round + " took: " + (roundEnd - roundStart) + " ms");
            }

            executor.shutdown();
            long totalEnd = System.currentTimeMillis();
            System.out.println("Total Test Time: " + (totalEnd - totalStart) + " ms");
        };
    }

    @Service
    public static class FlagEvaluationService {
        private final Client client;

        public FlagEvaluationService(Client client) {
            this.client = client;
        }

        public boolean getFlagValue(String flagKey) {
            return client.getBooleanValue(flagKey, false);
        }
    }
}
