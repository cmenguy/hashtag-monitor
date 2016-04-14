package com.cmenguy.monitor.hashtags.server.api;

import com.cmenguy.monitor.hashtags.common.Constants;
import com.google.common.base.Joiner;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A wrapper around the HttpClient to provide standardized functionality and also use a custom connection manager.
 */
public class SafeHttpClient {
    private final CloseableHttpClient client;
    private final int maxRetries;

    private final static Logger logger = LoggerFactory.getLogger(SafeHttpClient.class);

    public SafeHttpClient(int maxTotal, int maxPerRoute, int maxRetries) {
        // since it is shared across multiple threads, need to use a pool
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(maxTotal);
        cm.setDefaultMaxPerRoute(maxPerRoute);
        this.client = HttpClients.custom()
                .setConnectionManager(cm)
                .setRedirectStrategy(new LaxRedirectStrategy()) // redirect in case trailing / is missing
                .build();
        this.maxRetries = maxRetries;
    }

    private void submit(HttpContext context, HttpRequestBase request, String url, int expectedCode) {
        int attempt = 0;
        boolean success = false;
        while (attempt < maxRetries && !success) {
            try {
                HttpResponse response = client.execute(request, context);
                int statusCode = response.getStatusLine().getStatusCode();
                success = statusCode == expectedCode; // only exit if status code is 200
            } catch (Exception e) {
                logger.error("error sending HTTP request to url " + url, e);
            }
            attempt++;
        }
        if (!success) {
            throw new RuntimeException("HTTP request not sent to " + url);
        }
    }

    // create a string out of the request params
    private String formatRequestParams(Map<String, String> requestParams) {
        return "?" + Joiner.on("&").withKeyValueSeparator("=").join(requestParams);
    }

    public void post(
            HttpContext context,
            byte[] payload,
            String url,
            boolean isGzipped,
            int expectedCode,
            Map<String, String> requestParams) {
        String formattedUrl = url + formatRequestParams(requestParams);
        HttpPost post = new HttpPost(formattedUrl);
        ByteArrayEntity entity = new ByteArrayEntity(payload);
        entity.setContentType(Constants.POST_TYPE_BINARY);
        if (isGzipped) {
            entity.setContentEncoding(Constants.POST_ENCODING_GZIP);
        }

        post.setEntity(entity);
        submit(context, post, formattedUrl, expectedCode);
    }
}
