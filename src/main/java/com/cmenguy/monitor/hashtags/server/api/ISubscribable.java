package com.cmenguy.monitor.hashtags.server.api;

import com.typesafe.config.Config;
import org.eclipse.jetty.server.handler.ContextHandler;

public interface ISubscribable {
    ContextHandler handleRegister(Config config);
    ContextHandler handleDeregister(Config config);
    ContextHandler handleModify(Config config);
}
