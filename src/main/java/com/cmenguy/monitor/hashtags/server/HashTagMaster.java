package com.cmenguy.monitor.hashtags.server;

import com.cmenguy.monitor.hashtags.common.Twitter;
import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.cmenguy.monitor.hashtags.server.core.DispatchMonitor;
import com.cmenguy.monitor.hashtags.server.core.Dispatcher;
import com.cmenguy.monitor.hashtags.server.core.QueueMonitor;
import com.cmenguy.monitor.hashtags.server.stream.ITweetStream;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
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

public class HashTagMaster {
    private static Logger _logger = LoggerFactory.getLogger(HashTagMaster.class);

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

            int maxQueueSize = config.getInt("Master.Stream.queueSize");
            BlockingQueue<Tweet> objQueue = new LinkedBlockingQueue<Tweet>(maxQueueSize);

            String streamType = StringUtils.capitalize(config.getString("Master.Stream.origin")) + "Stream";
            String resolvedStreamType = config.getString("Master.Stream.package") + "." + streamType;
            ITweetStream stream = (ITweetStream)Class.forName(resolvedStreamType).newInstance();
            stream.initialize(config, objQueue);
            stream.startStream();

            Runnable queueMonitor = new QueueMonitor(objQueue, monitorFreqMillis);
            new Thread(queueMonitor).start();

            List<String> ring = config.getStringList("Server.Topology.ring");
            Map<String, AtomicLong> counters = getRingCounters(ring);

            Runnable dispatchMonitor = new DispatchMonitor(counters, monitorFreqMillis);
            new Thread(dispatchMonitor).start();

            int numDispatchers = config.getInt("Master.numDispatchers");
            ExecutorService executor = Executors.newFixedThreadPool(numDispatchers);
            for (int jobId = 0; jobId < numDispatchers; jobId++) {
                Runnable dispatcher = new Dispatcher(stream, objQueue, ring, counters);
                executor.execute(dispatcher);
            }


        } catch (Exception e) {
            _logger.error("Error starting hashtag master...", e);
        }
    }

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load(); // loads from -Dconfig.file=...
        new HashTagMaster().start(config);
    }
}
