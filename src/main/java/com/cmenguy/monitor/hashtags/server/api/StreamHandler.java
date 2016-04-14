package com.cmenguy.monitor.hashtags.server.api;

import com.cmenguy.monitor.hashtags.common.Twitter.Tweet;
import com.cmenguy.monitor.hashtags.server.core.BusManager;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import org.apache.commons.collections.Buffer;
import org.apache.commons.collections.BufferUtils;
import org.apache.commons.collections.buffer.BoundedFifoBuffer;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Queue;
import java.util.zip.GZIPInputStream;

public class StreamHandler extends AbstractHandler {
    private final int onSuccessCode;
    private final boolean isGzipped;
    private final String requestParamKey;

    private final static Logger logger = LoggerFactory.getLogger(StreamHandler.class);

    public StreamHandler(int onSuccessCode, boolean isGzipped, String requestParamKey) {
        this.onSuccessCode = onSuccessCode;
        this.isGzipped = isGzipped;
        this.requestParamKey = requestParamKey;
    }

    private byte[] getPayload(HttpServletRequest request) throws IOException {
        if (!isGzipped) {
            return IOUtils.toByteArray(request.getInputStream());
        } else {
            GZIPInputStream gzis = new GZIPInputStream(request.getInputStream());
            return IOUtils.toByteArray(gzis);
        }
    }

    public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
        String hashtag = request.getParameter(requestParamKey);
        byte[] payload = getPayload(request);
        //Tweet tweet = Tweet.parseFrom(payload);
        //logger.info("hashtag: " + hashtag + ", tweet: '" + tweet.getText() + "', id: '" + snowflakeId + "'");

        BusManager.INSTANCE.post(hashtag, payload);

        response.setStatus(onSuccessCode);
        baseRequest.setHandled(true);
    }
}
