package com.cmenguy.monitor.hashtags.server.stream;

import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StreamingEndpoint;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

public class TwitterStream implements ITweetStream {

    private Client _hosebirdClient = null;
    private static Logger _logger = LoggerFactory.getLogger(TwitterStream.class);

    public boolean initialize(Config config, BlockingQueue<Tweet> queue) {
        try {
            String applicationName = config.getString("Master.Stream.applicationName");
            String host = config.getString("Master.Stream.host");
            String endpoint = StringUtils.capitalize(config.getString("Master.Stream.endpoint"));
            String consumerKey = config.getString("Master.Stream.consumerKey");
            String consumerSecret = config.getString("Master.Stream.consumerSecret");
            String token = config.getString("Master.Stream.token");
            String tokenSecret = config.getString("Master.Stream.tokenSecret");
            _logger.info("Attempting to initialize connection to Twitter Streaming API...");
            _logger.info(consumerKey);
            _logger.info(consumerSecret);
            _logger.info(token);
            _logger.info(tokenSecret);

            Hosts hosebirdHosts = new HttpHosts(host);
            String resolvedEndpoint = "com.twitter.hbc.core.endpoint.Statuses" + endpoint + "Endpoint";
            StreamingEndpoint streamingEndpoint = (StreamingEndpoint) Class.forName(resolvedEndpoint).newInstance();
            Authentication auth = new OAuth1(consumerKey, consumerSecret, token, tokenSecret);

            _hosebirdClient = new ClientBuilder()
                    .name(applicationName)
                    .hosts(hosebirdHosts)
                    .authentication(auth)
                    .endpoint(streamingEndpoint)
                    .processor(new TweetDelimitedProcessor(queue))
                    .build();

            return true;
        } catch (Exception e) {
            _logger.error("Error initializing Twitter Streaming API connection", e);
        }
        return false;
    }

    public void startStream() {
        try {
            _logger.info("Attempting to connect to Twitter Streaming API");
            _hosebirdClient.connect();
        } catch (Exception e) {
            _logger.error("Error connecting to Twitter Streaming API", e);
        }
    }

    public boolean isFinished() {
        return _hosebirdClient.isDone();
    }
}
