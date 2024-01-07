package io.github.smyrgeorge.actor4k.examples.java;

import io.github.smyrgeorge.actor4k.actor.Actor;
import org.jetbrains.annotations.NotNull;

public class JActor extends Actor {
    public JActor(@NotNull String shard, @NotNull String key) {
        super(shard, key);
    }

    @NotNull
    @Override
    public Response onReceive(@NotNull Actor.Message m, @NotNull Actor.Response.Builder r) {
        return r.value("Hello from the a Java actor!").build();
    }
}
