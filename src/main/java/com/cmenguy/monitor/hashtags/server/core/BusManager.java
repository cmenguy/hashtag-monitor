package com.cmenguy.monitor.hashtags.server.core;

import com.cmenguy.monitor.hashtags.server.api.SafeHttpClient;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Generic singleton used to interact between the clients and our stream of data.
 */
public enum BusManager {
    INSTANCE;

    private Executor executor;
    private SafeHttpClient client;
    // hashtag to bus
    private final ConcurrentHashMap<String, AsyncEventBus> topics = new ConcurrentHashMap<String, AsyncEventBus>();
    // endpoint to listener
    private final ConcurrentHashMap<String, EventListener> subscribers = new ConcurrentHashMap<String, EventListener>();
    // endpoint to list of hashtags
    private final ConcurrentHashMap<String, Set<String>> subscriptions = new ConcurrentHashMap<String, Set<String>>();

    private final static Logger logger = LoggerFactory.getLogger(BusManager.class);

    public BusManager withExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    public BusManager withHttpClient(SafeHttpClient client) {
        this.client = client;
        return this;
    }

    private AsyncEventBus getOrCreate(String topicName) {
        AsyncEventBus bus = new AsyncEventBus(executor);
        AsyncEventBus previous = topics.putIfAbsent(topicName, bus);
        return null == previous ? bus : previous;
    }

    private EventListener getOrCreateListener(String endpoint) {
        EventListener subscriber = new EventListener(endpoint, client);
        EventListener previous = subscribers.putIfAbsent(endpoint, subscriber);
        return null == previous ? subscriber : previous;
    }

    public void post(String topicName, byte[] payload) {
        AsyncEventBus bus = getOrCreate(topicName);
        bus.post(payload);
    }

    public synchronized void register(String topicName, String endpoint) {
        AsyncEventBus bus = getOrCreate(topicName);
        if (!subscriptions.containsKey(endpoint)) {
            subscriptions.put(endpoint, new HashSet<String>());
        }
        if (!subscriptions.get(endpoint).contains(topicName)) {
            EventListener subscriber = getOrCreateListener(endpoint);
            subscriptions.get(endpoint).add(topicName);
            bus.register(subscriber);
            logger.info("registered endpoint " + endpoint + " to topic " + topicName);
        } else {
            logger.warn("attempting to register endpoint " + endpoint + " to topic " + topicName
                    + " but it already exists");
        }
    }

    public synchronized void register(Iterable<String> topicNames, String endpoint) {
        for (String topicName : topicNames) {
            register(topicName, endpoint);
        }
    }

    public synchronized void deregister(String topicName, String endpoint) {
        AsyncEventBus bus = getOrCreate(topicName);
        EventListener subscriber = subscribers.get(endpoint);
        if (subscriptions.get(endpoint).contains(topicName)) {
            bus.unregister(subscriber);
            subscriptions.get(endpoint).remove(topicName);
            logger.info("deregistered endpoint " + endpoint + " to topic " + topicName);
        } else {
            logger.warn("attempting to deregister endpoint " + endpoint + " to topic " + topicName
                    + " but it doesn't exist");
        }
    }

    public synchronized void deregister(Iterable<String> topicNames, String endpoint) {
        for (String topicName : topicNames) {
            deregister(topicName, endpoint);
        }
    }

    public synchronized void deregister(String endpoint) {
        Set<String> topicNames = subscriptions.get(endpoint);
        deregister(topicNames, endpoint);
    }

    public synchronized void modify(Iterable<String> topicNames, String endpoint) {
        Set<String> topicNamesSet = Sets.newHashSet(topicNames);
        Set<String> existingSubscriptions = subscriptions.get(endpoint);
        Set<String> topicsToAdd = Sets.difference(topicNamesSet, existingSubscriptions);
        Set<String> topicsToRemove = Sets.difference(existingSubscriptions, topicNamesSet);
        deregister(topicsToRemove, endpoint);
        register(topicsToAdd, endpoint);
    }
}
