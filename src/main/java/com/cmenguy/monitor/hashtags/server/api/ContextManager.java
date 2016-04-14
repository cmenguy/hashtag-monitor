package com.cmenguy.monitor.hashtags.server.api;

import com.typesafe.config.Config;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import java.util.ArrayList;
import java.util.List;

public class ContextManager implements IStreamable {

    public ContextHandler handleStream(Config config) {
        String endpoint = "/" + config.getString("Protocol.Internal.endpoint");
        int onSuccessCode = config.getInt("Protocol.Internal.onSuccessCode");
        boolean isGzipped = config.getBoolean("Protocol.Internal.gzipPayload");
        String requestParamKey = config.getString("Protocol.Internal.requestParamKey");
        ContextHandler context = new ContextHandler(endpoint);
        context.setHandler(new StreamHandler(onSuccessCode, isGzipped, requestParamKey));
        return context;
    }

    public ContextHandlerCollection setup(Config config) {
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        List<Handler> handlers = new ArrayList<Handler>();
        handlers.add(handleStream(config));
        contexts.setHandlers(handlers.toArray(new Handler[handlers.size()]));
        return contexts;
    }
}
