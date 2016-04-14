package com.cmenguy.monitor.hashtags.server.api;

import java.util.List;

public interface IClientFacing {
    void register(List<String> hashtags, String endpoint);
    void deregister();
    void update();
}
