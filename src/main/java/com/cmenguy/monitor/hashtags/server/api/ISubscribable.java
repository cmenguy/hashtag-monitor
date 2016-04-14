package com.cmenguy.monitor.hashtags.server.api;

import com.typesafe.config.Config;
import org.eclipse.jetty.server.handler.ContextHandler;

/**
 * Represents an object that can be subscribed to by clients.
 */
public interface ISubscribable {
    // handle a request for registration
    ContextHandler handleRegister(Config config);
    // handle a request for deregistration
    ContextHandler handleDeregister(Config config);
    // handle a request for modifying subscriptions
    ContextHandler handleModify(Config config);
}
