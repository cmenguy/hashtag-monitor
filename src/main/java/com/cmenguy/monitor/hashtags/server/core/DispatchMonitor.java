package com.cmenguy.monitor.hashtags.server.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A running thread used to monitor the distribution of our data in the cluster.
 */
public class DispatchMonitor implements Runnable {
    private final Map<String, AtomicLong> counters;
    private final long monitorFreqMillis;

    private final static Logger logger = LoggerFactory.getLogger(DispatchMonitor.class);

    public DispatchMonitor(Map<String, AtomicLong> counters, long monitorFreqMillis) {
        this.counters = counters;
        this.monitorFreqMillis = monitorFreqMillis;
    }

    public void run() {
        while (true) {
            try {
                for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
                    logger.info(entry.getKey() + " -> " + entry.getValue().toString() + "tweets");
                }
                Thread.sleep(monitorFreqMillis);
            } catch (Exception e) {

            }
        }
    }
}
