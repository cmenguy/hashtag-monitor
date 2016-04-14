package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.common.Constants;
import com.cmenguy.monitor.hashtags.common.Twitter.Hashtag;
import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.cmenguy.monitor.hashtags.server.api.SafeHttpClient;
import com.cmenguy.monitor.hashtags.server.stream.ITweetStream;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * Main worker thread used to determine which node should receive the request and send it.
 */
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

    // send a protobuf payload to a given server
    public void dispatch(byte[] obj, String hashtag, String host) {
        String url = Constants.HTTP_URI + host + "/" + streamEndpoint + "/"; // trailing / is important !
        Map<String, String> requestParams = ImmutableMap.of(requestParamKey, hashtag);
        client.post(context, obj, url, isGzipped, expectedCode, requestParams);
        counters.get(host).incrementAndGet(); // increment counter for our monitor thread
    }

    // simply use modulus to figure out a random server among all of them
    private int computeBucket(String hashtag) {
        return (hashtag.hashCode() & 0xfffffff) % ringSize; // account for negative hashcodes
    }

    // go from the object representation to a pure byte array serialized in protobuf
    private byte[] getPayload(Tweet obj) throws IOException {
        byte[] bytes;
        if (!isGzipped) { // if we decided to not gzip there's nothing to do
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
                // standard protobuf-provided method to serialize
                byte[] payload = getPayload(tweet);

                Function<Hashtag, String> hashtagToName =
                        new Function<Hashtag, String>() {
                            public String apply(Hashtag ht) { return ht.getText(); }
                        };

                // if a tweet has the same hashtag multiple times, we only need to process it once so use a set
                Set<String> hashtags = FluentIterable
                        .from(tweet.getEntities().getHashtagsList())
                        .transform(hashtagToName)
                        .toSet();

                for (String hashtag : hashtags) {
                    try {
                        int bucket = computeBucket(hashtag);
                        String fullHost = ring.get(bucket);
                        dispatch(payload, hashtag, fullHost);
                    } catch (Exception e) {
                        logger.error("error while dispatching tweet for hashtag " + hashtag, e);
                    }
                }
            } catch (Exception e) {
                logger.error("error while dispatching tweet from stream", e);
            }
        }
    }
}
