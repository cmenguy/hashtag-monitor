package com.cmenguy.monitor.hashtags.server.api;

import com.cmenguy.monitor.hashtags.common.Constants;
import com.cmenguy.monitor.hashtags.common.Twitter;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SafeHttpClient {
    private final CloseableHttpClient client;
    private final int maxRetries;

    private final static Logger logger = LoggerFactory.getLogger(SafeHttpClient.class);

    public SafeHttpClient(int maxTotal, int maxPerRoute, int maxRetries) {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(maxTotal);
        cm.setDefaultMaxPerRoute(maxPerRoute);
        this.client = HttpClients.custom()
                .setConnectionManager(cm)
                .setRedirectStrategy(new LaxRedirectStrategy())
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
                success = statusCode == expectedCode;
            } catch (Exception e) {
                logger.error("error sending HTTP request to url " + url, e);
            }
            attempt++;
        }
        if (!success) {
            throw new RuntimeException("HTTP request not sent to " + url);
        }
    }

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
