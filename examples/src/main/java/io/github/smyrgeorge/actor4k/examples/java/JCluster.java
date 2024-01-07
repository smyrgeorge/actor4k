package io.github.smyrgeorge.actor4k.examples.java;

import io.github.smyrgeorge.actor4k.actor.Actor;
import io.github.smyrgeorge.actor4k.system.ActorRegistry;

public class JCluster {
    public static void main(String[] args) {
        Actor.Ref ref = ActorRegistry.INSTANCE.asJava().get(JActor.class, "ACC00011").join();
        System.out.println(ref);
        String res = (String) ref.asJava().ask("Tell me something").join();
        System.out.println(res);
    }
}
