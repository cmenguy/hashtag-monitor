package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.common.Constants;
import com.cmenguy.monitor.hashtags.common.Twitter.Hashtag;
import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.cmenguy.monitor.hashtags.server.api.SafeHttpClient;
import com.cmenguy.monitor.hashtags.server.stream.ITweetStream;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

public class Dispatcher implements Runnable {
    private final ITweetStream stream;
    private final BlockingQueue<Tweet> queue;
    private final List<String> ring;
    private final int ringSize;
    private final Map<String, AtomicLong> counters;
    private final HttpContext context;
    private final SafeHttpClient client;
    private final String streamEndpoint;
    private final int expectedCode;
    private final boolean isGzipped;
    private final String requestParamKey;

    private static Logger logger = LoggerFactory.getLogger(Dispatcher.class);

    public Dispatcher(ITweetStream stream,
                      BlockingQueue<Tweet> queue,
                      List<String> ring,
                      Map<String, AtomicLong> counters,
                      SafeHttpClient client,
                      String streamEndpoint,
                      int expectedCode,
                      boolean isGzipped,
                      String requestParamKey) {
        this.stream = stream;
        this.queue = queue;
        this.ring = ring;
        this.ringSize = ring.size();
        this.counters = counters;
        this.context = HttpClientContext.create();
        this.client = client;
        this.streamEndpoint = streamEndpoint;
        this.expectedCode = expectedCode;
        this.isGzipped = isGzipped;
        this.requestParamKey = requestParamKey;
    }

    public void dispatch(byte[] obj, String hashtag, String host) {
        String url = Constants.HTTP_URI + host + "/" + streamEndpoint + "/";
        Map<String, String> requestParams = ImmutableMap.of(requestParamKey, hashtag);
        client.post(context, obj, url, isGzipped, expectedCode, requestParams);
        counters.get(host).incrementAndGet();
    }

    private int computeBucket(String hashtag) {
        return (hashtag.hashCode() & 0xfffffff) % ringSize; // account for negative hashcodes
    }

    private byte[] getPayload(Tweet obj) throws IOException {
        byte[] bytes;
        if (!isGzipped) {
            bytes = obj.toByteArray();
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzos = new GZIPOutputStream(baos);
            obj.writeTo(gzos);
            bytes = baos.toByteArray();
        }
        return bytes;
    }

    public void run() {
        while (!stream.isFinished()) {
            try {
                Tweet tweet = queue.take();
                byte[] payload = getPayload(tweet);

                Function<Hashtag, String> hashtagToName =
                        new Function<Hashtag, String>() {
                            public String apply(Hashtag ht) { return ht.getText(); }
                        };

                List<String> hashtags = Lists.transform(tweet.getEntities().getHashtagsList(), hashtagToName);
                for (String hashtag : hashtags) {
                    try {
                        int bucket = computeBucket(hashtag);
                        String fullHost = ring.get(bucket);
                        dispatch(payload, hashtag, fullHost);
                    } catch (Exception e) {
                        logger.error("error while dispatching tweet for hashtah " + hashtag, e);
                    }
                }
            } catch (Exception e) {
                logger.error("error while dispatching tweet from stream", e);
            }
        }
    }
}
