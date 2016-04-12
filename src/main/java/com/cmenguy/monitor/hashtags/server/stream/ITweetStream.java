package com.cmenguy.monitor.hashtags.server.stream;

import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.typesafe.config.Config;

import java.util.concurrent.BlockingQueue;

public interface ITweetStream {
    boolean initialize(Config config, BlockingQueue<Tweet> queue);
    void startStream();
    boolean isFinished();
}
