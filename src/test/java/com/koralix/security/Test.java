package com.koralix.security;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Test {

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        SimpleSecurityProvider securityProvider = new SimpleSecurityProvider("sign key", 0L, SimpleSecurityProvider.Expiration.FOREVER); // TODO: Replace with your own key and salt.
        MinecraftAPI client = MinecraftAPI.client(executorService);
        MinecraftAPI server = MinecraftAPI.server(executorService, securityProvider);
        client.join(
                "auth token",
                "uuid",
                "username"
        ).thenApply(serverId -> server.hasJoined("username", serverId)
                .thenApply(securityProvider::generateToken)
                .thenAccept(token -> {
                    System.out.println("Token 1: " + token);
                    System.out.println("Valid 1: " + securityProvider.verifyToken(token));
                    Optional<String> token2 = securityProvider.getHidIfValid(token).map(securityProvider::generateToken);
                    if (token2.isEmpty()) {
                        System.out.println("Token 2: null");
                        System.out.println("Valid 2: false");
                        return;
                    }
                    System.out.println("Token 2: " + token2.get());
                    System.out.println("Valid 2: " + securityProvider.verifyToken(token2.get()));
                    System.out.println("Token 1: " + token);
                    System.out.println("Valid 1: " + securityProvider.verifyToken(token));
                })
        ).join();
        executorService.shutdown();
    }

}
