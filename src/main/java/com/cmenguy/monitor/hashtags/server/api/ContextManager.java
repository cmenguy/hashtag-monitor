package com.cmenguy.monitor.hashtags.server.api;

import com.typesafe.config.Config;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import java.util.ArrayList;
import java.util.List;

public class ContextManager implements IStreamable, ISubscribable {

    public ContextHandlerCollection setup(Config config) {
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        List<Handler> handlers = new ArrayList<Handler>();
        handlers.add(handleStream(config));
        handlers.add(handleRegister(config));
        handlers.add(handleDeregister(config));
        handlers.add(handleModify(config));
        contexts.setHandlers(handlers.toArray(new Handler[handlers.size()]));
        return contexts;
    }

    public ContextHandler handleStream(Config config) {
        String endpoint = "/" + config.getString("Protocol.Internal.endpoint");
        int onSuccessCode = config.getInt("Protocol.Internal.onSuccessCode");
        boolean isGzipped = config.getBoolean("Protocol.Internal.gzipPayload");
        String requestParamKey = config.getString("Protocol.Internal.requestParamKey");
        ContextHandler context = new ContextHandler(endpoint);
        context.setHandler(new StreamHandler(onSuccessCode, isGzipped, requestParamKey));
        return context;
    }

    public ContextHandler handleRegister(Config config) {
        String endpoint = "/" + config.getString("Protocol.External.registerContext");
        int onSuccessCode = config.getInt("Protocol.External.onSuccessCode");
        boolean isGzipped = config.getBoolean("Protocol.External.gzipPayload");
        String hashtagsKey = config.getString("Protocol.External.hashtagsKey");
        String endpointKey = config.getString("Protocol.External.endpointKey");
        ContextHandler context = new ContextHandler(endpoint);
        context.setHandler(new RegisterHandler(onSuccessCode, isGzipped, hashtagsKey, endpointKey));
        return context;
    }

    public ContextHandler handleDeregister(Config config) {
        String endpoint = "/" + config.getString("Protocol.External.deregisterContext");
        int onSuccessCode = config.getInt("Protocol.External.onSuccessCode");
        boolean isGzipped = config.getBoolean("Protocol.External.gzipPayload");
        String hashtagsKey = config.getString("Protocol.External.hashtagsKey");
        String endpointKey = config.getString("Protocol.External.endpointKey");
        ContextHandler context = new ContextHandler(endpoint);
        context.setHandler(new DeregisterHandler(onSuccessCode, isGzipped, hashtagsKey, endpointKey));
        return context;
    }

    public ContextHandler handleModify(Config config) {
        String endpoint = "/" + config.getString("Protocol.External.modifyContext");
        int onSuccessCode = config.getInt("Protocol.External.onSuccessCode");
        boolean isGzipped = config.getBoolean("Protocol.External.gzipPayload");
        String hashtagsKey = config.getString("Protocol.External.hashtagsKey");
        String endpointKey = config.getString("Protocol.External.endpointKey");
        ContextHandler context = new ContextHandler(endpoint);
        context.setHandler(new ModifyHandler(onSuccessCode, isGzipped, hashtagsKey, endpointKey));
        return context;
    }
}
