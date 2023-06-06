# Simple Security Provider

This is a simple security provider based on Minecraft sessions.

## Simple use case

```java
ExecutorService executorService = Executors.newSingleThreadExecutor();
SimpleSecurityProvider securityProvider = new SimpleSecurityProvider("sign key", 0L); // TODO: Replace with your own key and salt.
MinecraftAPI client = MinecraftAPI.client(executorService);
MinecraftAPI server = MinecraftAPI.server(executorService, securityProvider);
client.join(
        "access token",
        "uuid",
        "username"
).thenApply(serverId -> server.hasJoined("username", serverId)
        .thenApply(securityProvider::generateToken)
        .thenAccept(System.out::println) // TODO: Send token to client.
).join();
executorService.shutdown();
```

- It's your work sending the username and serverId to the server.
- It's your work sending the token to the client.

## How to verify the token

```java
Optional<String> hid = securityProvider.getHidIfValid(token);
//or
boolean valid = securityProvider.verifyToken(token);
```

## When to regenerate the token

Each time the client uses the token, you should regenerate it.
```java
securityProvider.getHidIfValid(token).map(securityProvider::generateToken);
```

## Recommended initialization

All the initialization should be done in the same scope (e.g. in the main method). The initialization should be done in the following order:

1. Read the key and salt from a file with no read/write access to other users.
2. Create the security provider.
3. Return the security provider.

The all in same scope is important to remove as soon as possible the key and salt from the memory. 

## General recommendations

1. The key is a random string of any length. The salt is a random long. Create strong random values.
2. The salt must not change. The key can change, but it will invalidate all tokens.
