package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.server.api.SafeHttpClient;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Will listen for incoming tweet traffic and send it to the clients via HTTP POST.
 */
public class EventListener {

    private final String endpoint;
    private final SafeHttpClient client;
    private final HttpContext context;

    private final static Logger logger = LoggerFactory.getLogger(EventListener.class);

    public EventListener(String endpoint, SafeHttpClient client) {
        this.endpoint = endpoint;
        this.client = client;
        this.context = HttpClientContext.create();
    }

    @Subscribe
    @AllowConcurrentEvents
    public void task(byte[] payload) {
        // send a POST request containing the protobuf-serialized payload
        client.post(context, payload, endpoint, false, 200, null);

        // NOTE: this is used for experimental purposes and as such is left here, this is not dead code.
        //Tweet tweet = Tweet.parseFrom(payload);
        //logger.info("sending to endpoint " + endpoint + " tweet: '" + tweet.getText() + "'");
    }
}
