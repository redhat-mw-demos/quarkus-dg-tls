package org.acme.infinispan.client;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;

import io.quarkus.infinispan.client.Remote;
import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/infinispan")
public class InfinispanGreetingResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanGreetingResource.class);

    @Inject
    RemoteCacheManager cacheManager;

    AtomicBoolean running = new AtomicBoolean(false);

    void onStart(@Observes StartupEvent ev) {
        cache.put("hello", "Hello World, Infinispan is up!");
    }

    @Inject
    @Remote("default")
    RemoteCache<String, String> cache;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return cache.get("hello");
    }

    @POST
    @Path("/fill")
    @Produces(MediaType.TEXT_PLAIN)
    public String fillerup() throws Exception {

        if (running.getAndSet(true)) {
            return "Already running";
        }

        for (int i = 0; i < 1000; i++) {
            if (!running.get()) {
                return "Done";
            }
            char[] chars = new char[2 * 1024 * 1024];
            Arrays.fill(chars, 'f');
            cache.put(Math.random() + "", new String(chars));
            LOGGER.info("Added " + (2 * (i + 1)) + "MB");
            Thread.sleep(2000);
        }
        running.set(false);
        return "Adding 2GB of junk";
    }

    @POST
    @Path("/clear")
    @Produces(MediaType.TEXT_PLAIN)
    public String clear() throws Exception {

        running.set(false);
        cache.clear();
        return "Cleared cache";
    }

}
