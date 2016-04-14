package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.cmenguy.monitor.hashtags.server.api.SafeHttpClient;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        client.post(context, payload, endpoint, false, 200, null);
        //Tweet tweet = Tweet.parseFrom(payload);
        //logger.info("sending to endpoint " + endpoint + " tweet: '" + tweet.getText() + "'");
    }
}
