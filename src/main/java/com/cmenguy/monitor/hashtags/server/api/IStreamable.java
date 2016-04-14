package com.cmenguy.monitor.hashtags.server.api;

import com.typesafe.config.Config;
import org.eclipse.jetty.server.handler.ContextHandler;

/**
 * Represents an object that can be streamed to from the master.
 */
public interface IStreamable {
    // handle a stream of data
    ContextHandler handleStream(Config config);
}
