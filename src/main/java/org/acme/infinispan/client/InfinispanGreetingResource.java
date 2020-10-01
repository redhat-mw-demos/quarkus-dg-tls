package org.acme.infinispan.client;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.infinispan.client.hotrod.RemoteCache;

import io.quarkus.infinispan.client.Remote;

@Path("/infinispan")
public class InfinispanGreetingResource {

    @Inject
    @Remote("mycache")
    RemoteCache<String, String> cache;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return cache.get("hello");
    }
}
