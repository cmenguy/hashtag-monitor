package com.cmenguy.monitor.hashtags.server.api;

import com.typesafe.config.Config;
import org.eclipse.jetty.server.handler.ContextHandler;

public interface IStreamable {
    ContextHandler handleStream(Config config);
}
