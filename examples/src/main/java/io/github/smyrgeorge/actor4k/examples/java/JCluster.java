package io.github.smyrgeorge.actor4k.examples.java;

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef;
import io.github.smyrgeorge.actor4k.examples.AccountActor;
import io.github.smyrgeorge.actor4k.examples.Req;
import io.github.smyrgeorge.actor4k.examples.Resp;
import io.github.smyrgeorge.actor4k.system.ActorSystem;
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry;
import io.github.smyrgeorge.actor4k.system.stats.SimpleStats;

public class JCluster {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.INSTANCE
                .register(new SimpleStats())
                .register(new SimpleActorRegistry())
                .start();

        ActorRef ref = system.getRegistry().asJava().get(AccountActor.class, "ACC00011").join();
        System.out.println(ref);
        Req req = new Req("[tell] Hello World!");
        Resp resp = (Resp) ref.asJava().ask(req).join();
        System.out.println(resp);
    }
}
