package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventListener {

    private final String endpoint;

    private final static Logger logger = LoggerFactory.getLogger(EventListener.class);

    public EventListener(String endpoint) {
        this.endpoint = endpoint;
    }

    @Subscribe
    @AllowConcurrentEvents
    public void task(byte[] payload) {
        try {
            Tweet tweet = Tweet.parseFrom(payload);
            logger.info("sending to endpoint " + endpoint + " tweet: '" + tweet.getText() + "'");
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }
}
