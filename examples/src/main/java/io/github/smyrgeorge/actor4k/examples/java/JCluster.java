package io.github.smyrgeorge.actor4k.examples.java;

import io.github.smyrgeorge.actor4k.actor.Actor;
import io.github.smyrgeorge.actor4k.examples.AccountActor;
import io.github.smyrgeorge.actor4k.examples.Req;
import io.github.smyrgeorge.actor4k.examples.Resp;
import io.github.smyrgeorge.actor4k.system.ActorRegistry;

public class JCluster {
    public static void main(String[] args) {
        Actor.Ref ref = ActorRegistry.INSTANCE.asJava().get(AccountActor.class, "ACC00011").join();
        System.out.println(ref);
        Req req = new Req("[tell] Hello World!");
        Resp resp = (Resp) ref.asJava().ask(req).join();
        System.out.println(resp);
    }
}
