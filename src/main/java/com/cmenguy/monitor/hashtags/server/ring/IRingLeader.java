package com.cmenguy.monitor.hashtags.server.ring;

public interface IRingLeader {
    void registerSlave();
    void sendHeartBeat();

}
