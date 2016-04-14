package com.cmenguy.monitor.hashtags.server;

import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.cmenguy.monitor.hashtags.server.api.SafeHttpClient;
import com.cmenguy.monitor.hashtags.server.core.DispatchMonitor;
import com.cmenguy.monitor.hashtags.server.core.Dispatcher;
import com.cmenguy.monitor.hashtags.server.core.QueueMonitor;
import com.cmenguy.monitor.hashtags.server.stream.ITweetStream;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Master process responsible for streaming Twitter data and distributing it to the cluster.
 */
public class HashTagMaster {
    private static Logger logger = LoggerFactory.getLogger(HashTagMaster.class);

    // counters used for monitoring purposes
    private Map<String, AtomicLong> getRingCounters(List<String> ring) {
        Function<String, AtomicLong> serverToCounter =
                new Function<String, AtomicLong>() {
                    public AtomicLong apply(String server) { return new AtomicLong(); }
                };
        return Maps.toMap(ring, serverToCounter);
    }

    public void start(Config config) {
        try {
            long monitorFreqMillis = config.getDuration("Master.monitorFrequency").toMillis();
            int maxRetries = config.getInt("Master.maxRetries");

            // use a blocking queue so we wait on both sides if we consume too quickly or too fast
            int maxQueueSize = config.getInt("Master.Stream.queueSize");
            BlockingQueue<Tweet> objQueue = new LinkedBlockingQueue<Tweet>(maxQueueSize);

            // use reflexion to figure out the type of stream we use (only Twitter implemented currently)
            String streamType = StringUtils.capitalize(config.getString("Master.Stream.origin")) + "Stream";
            String resolvedStreamType = config.getString("Master.Stream.package") + "." + streamType;
            ITweetStream stream = (ITweetStream)Class.forName(resolvedStreamType).newInstance();

            // establish the connection
            stream.initialize(config, objQueue);
            // open the flood gates
            stream.startStream();

            // a simple thread monitor to keep an eye on the size of the queue at regular intervals
            Runnable queueMonitor = new QueueMonitor(objQueue, monitorFreqMillis);
            new Thread(queueMonitor).start();

            // a simple thread monitor to keep an eye on the distribution of our data across the cluster
            List<String> ring = config.getStringList("Server.Topology.ring");
            Map<String, AtomicLong> counters = getRingCounters(ring);
            int numSlaves = ring.size();
            Runnable dispatchMonitor = new DispatchMonitor(counters, monitorFreqMillis);
            new Thread(dispatchMonitor).start();

            // use our wrapper around HttpClient and pass it downstream
            SafeHttpClient httpClient = new SafeHttpClient(numSlaves, numSlaves, maxRetries);

            int numDispatchers = config.getInt("Master.numDispatchers");
            String streamEndpoint = config.getString("Protocol.Internal.endpoint");
            int expectedCode = config.getInt("Protocol.Internal.onSuccessCode");
            boolean isGzipped = config.getBoolean("Protocol.Internal.gzipPayload");
            String requestParamKey = config.getString("Protocol.Internal.requestParamKey");

            // the dispatchers are running forever, so a fixed size thread pool is enough
            ExecutorService executor = Executors.newFixedThreadPool(numDispatchers);
            for (int jobId = 0; jobId < numDispatchers; jobId++) {
                Runnable dispatcher = new Dispatcher(
                        stream, objQueue, ring, counters, httpClient, streamEndpoint,
                        expectedCode, isGzipped, requestParamKey
                );
                executor.execute(dispatcher);
            }
        } catch (Exception e) {
            logger.error("Error starting hashtag master...", e);
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load(); // loads from -Dconfig.file=...
        new HashTagMaster().start(config);
    }
}
