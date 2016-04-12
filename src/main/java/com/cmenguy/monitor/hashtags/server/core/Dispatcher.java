package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.common.Twitter.Hashtag;
import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.cmenguy.monitor.hashtags.server.stream.ITweetStream;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class Dispatcher implements Runnable {
    private ITweetStream stream;
    private BlockingQueue<Tweet> queue;
    private List<String> ring;
    private int ringSize;
    private Map<String, AtomicLong> counters;
    private HttpContext context;

    private static Logger logger = LoggerFactory.getLogger(Dispatcher.class);

    public Dispatcher(ITweetStream stream, BlockingQueue<Tweet> queue, List<String> ring, Map<String, AtomicLong> counters) {
        this.stream = stream;
        this.queue = queue;
        this.ring = ring;
        this.ringSize = ring.size();
        this.counters = counters;
        this.context = HttpClientContext.create();
    }

    public void dispatch(byte[] obj, String host) {
        counters.get(host).incrementAndGet();
    }

    private int computeBucket(String hashtag) {
        return (hashtag.hashCode() & 0xfffffff) % ringSize; // account for negative hashcodes
    }

    public void run() {
        while (!stream.isFinished()) {
            try {
                Tweet tweet = queue.take();
                byte[] payload = tweet.toByteArray();

                Function<Hashtag, String> hashtagToName =
                        new Function<Hashtag, String>() {
                            public String apply(Hashtag ht) { return ht.getText(); }
                        };

                List<String> hashtags = Lists.transform(tweet.getEntities().getHashtagsList(), hashtagToName);
                for (String hashtag : hashtags) {
                    int bucket = computeBucket(hashtag);
                    String fullHost = ring.get(bucket);
                    dispatch(payload, fullHost);
                }
            } catch (Exception e) {
                logger.error("error while dispatching tweet from stream", e);
            }
        }
    }
}
