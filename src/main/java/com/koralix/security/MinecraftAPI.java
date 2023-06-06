package com.koralix.security;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Handler to communicate with Mojang Session Services.
 *
 * @since 1.0.0
 * @author JohanVonElectrum
 */
public final class MinecraftAPI {

    private final ExecutorService executorService;
    private final @Nullable SimpleSecurityProvider securityProvider;
    private final boolean isServer;

    private MinecraftAPI(@NotNull ExecutorService executorService, @Nullable SimpleSecurityProvider securityProvider, boolean isServer) {
        this.executorService = executorService;
        this.securityProvider = securityProvider;
        this.isServer = isServer;
    }

    /**
     * Creates a new MinecraftAPI instance for a client.
     * @param executorService The executor service to use for async operations.
     * @return A new MinecraftAPI instance.
     */
    public static @NotNull MinecraftAPI client(@NotNull ExecutorService executorService) {
        return new MinecraftAPI(executorService, null, false);
    }

    /**
     * Creates a new MinecraftAPI instance for a server.
     * @param executorService The executor service to use for async operations.
     * @param securityProvider The security provider to use for authentication.
     * @return A new MinecraftAPI instance.
     */
    public static @NotNull MinecraftAPI server(@NotNull ExecutorService executorService, @NotNull SimpleSecurityProvider securityProvider) {
        return new MinecraftAPI(executorService, securityProvider, true);
    }

    /**
     * The client sends a join request to the Mojang session server.
     * @param token The client's access token.
     * @param uuid The client's UUID.
     * @param username The client's username.
     * @return A CompletableFuture that completes with the server ID, or an exception if the request failed.
     */
    public @NotNull CompletableFuture<String> join(@NotNull String token, @NotNull String uuid, @NotNull String username) {
        if (isServer) {
            throw new IllegalStateException("This method can only be called on a client.");
        }

        String serverId = CryptoUtils.randomHex(20);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://sessionserver.mojang.com/session/minecraft/join")
                .post(RequestBody.create("{\"accessToken\":\"" + token +
                                "\",\"selectedProfile\":\"" + uuid +
                                "\",\"serverId\":\"" + serverId +
                                "\",\"username\":\"" + username + "\"}",
                        MediaType.get("application/json"))
                ).build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.newCall(request).execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executorService).thenApply(response -> {
            if (response.code() >= 200 && response.code() < 300) {
                return serverId;
            } else {
                throw new RuntimeException("Invalid response code: " + response.code());
            }
        });
    }

    /**
     * The server checks if the client is trying to join with the correct server ID.
     * @param username The client's username.
     * @param serverId The server ID.
     * @return A CompletableFuture that completes with the client's HID, or an exception if the request failed.
     */
    public @NotNull CompletableFuture<String> hasJoined(@NotNull String username, @NotNull String serverId) {
        if (!isServer) {
            throw new IllegalStateException("This method can only be called on a server.");
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" + username + "&serverId=" + serverId)
                .get()
                .build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.newCall(request).execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executorService).thenApply(response -> {
            if (response.code() >= 200 && response.code() < 300) {
                try {
                    return response.body().string()
                            .replaceAll("[\\s\\n]", "")
                            .replaceAll("(?:.*)id\":\"([0-9a-f]+)\"(?:.*)", "$1");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("Invalid response code: " + response.code());
            }
        }).thenApply(uuid -> {
            if (uuid == null || uuid.isEmpty()) {
                throw new RuntimeException("Invalid response body.");
            }

            StringBuilder formattedUUID = new StringBuilder(uuid);
            formattedUUID.insert(8, "-");
            formattedUUID.insert(13, "-");
            formattedUUID.insert(18, "-");
            formattedUUID.insert(23, "-");

            return UUID.fromString(formattedUUID.toString());
        }).thenApply(uuid -> {
            try {
                return securityProvider.getHid(uuid);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
