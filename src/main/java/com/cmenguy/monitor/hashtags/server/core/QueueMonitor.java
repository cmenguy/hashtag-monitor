package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * A thread used to monitor the size of the queue at regular intervals.
 */
public class QueueMonitor implements Runnable {
    private BlockingQueue<Tweet> queue;
    private long monitorFreqMillis;

    private static Logger logger = LoggerFactory.getLogger(QueueMonitor.class);

    public QueueMonitor(BlockingQueue<Tweet> queue, long monitorFreqMillis) {
        this.queue = queue;
        this.monitorFreqMillis = monitorFreqMillis;
    }
    public void run() {
        while (true) {
            try {
                int size = queue.size();
                logger.info("queue size: " + size);
                Thread.sleep(monitorFreqMillis); // every 10 seconds
            } catch (Exception e) {
                logger.error("error getting queue size", e);
            }
        }
    }
}
