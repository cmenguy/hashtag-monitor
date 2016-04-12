package com.cmenguy.monitor.hashtags.server.stream;

import com.cmenguy.monitor.hashtags.common.JsonUtils;
import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.protobuf.format.JsonFormat;
import com.twitter.hbc.common.DelimitedStreamReader;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.processor.AbstractProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class TweetDelimitedProcessor extends AbstractProcessor<Tweet> {
    private final static Logger logger = LoggerFactory.getLogger(TweetDelimitedProcessor.class);
    private final static int DEFAULT_BUFFER_SIZE = 50000;
    private final static int MAX_ALLOWABLE_BUFFER_SIZE = 500000;
    private final static String EMPTY_LINE = "";

    private DelimitedStreamReader reader;

    public TweetDelimitedProcessor(BlockingQueue<Tweet> queue) {
        super(queue);
    }

    @Nullable
    protected Tweet processNextMessage() throws IOException {
        int delimitedCount = -1;
        int retries = 0;
        while (delimitedCount < 0 && retries < 3) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Unable to read new line from stream");
            } else if (line.equals(EMPTY_LINE)) {
                return null;
            }

            try {
                delimitedCount = Integer.parseInt(line);
            } catch (NumberFormatException n) {
                // resilience against the occasional malformed message
                logger.warn("Error parsing delimited length", n);
            }
            retries += 1;
        }

        if (delimitedCount < 0) {
            throw new RuntimeException("Unable to process delimited length");
        }

        if (delimitedCount > MAX_ALLOWABLE_BUFFER_SIZE) {
            // this is to protect us from nastiness
            throw new IOException("Unreasonable message size " + delimitedCount);
        }
        String content = reader.read(delimitedCount);

        // use JSON lib to fix encoding issues and have an obj representation
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<HashMap<String, Object>> typeRef
                = new TypeReference<HashMap<String, Object>>() {
        };
        Map<String, Object> map = mapper.readValue(content, typeRef);
        // nulls and empty lists/nodes are treated differently in proto 3, need to remove them
        JsonUtils.removeEmptyLists(map);
        String cleanContent = mapper.writeValueAsString(map);

        // convert cleaned tree representation to a protobuf object
        Tweet.Builder builder = Tweet.newBuilder();
        JsonFormat jsonFormat = new JsonFormat();
        jsonFormat.merge(cleanContent, builder);

        Tweet tweet = builder.build();
        // only keep objects where we have at least 1 hashtag (the others would be thrown away)
        if (null == tweet.getEntities() || 0 == tweet.getEntities().getHashtagsCount()) {
            return processNextMessage();
        }
        // return a tweet protobuf object with at least 1 hashtag
        return tweet;
    }

    public void setup(InputStream input) {
        reader = new DelimitedStreamReader(input, Constants.DEFAULT_CHARSET, DEFAULT_BUFFER_SIZE);
    }
}
