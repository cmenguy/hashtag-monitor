package com.cmenguy.monitor.hashtags.server.api;

import com.cmenguy.monitor.hashtags.server.core.BusManager;
import com.google.common.base.Splitter;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Called whenever we get a modify request.
 */
public class ModifyHandler extends AbstractHandler {

    private final int onSuccessCode;
    private final boolean isGzipped;
    private final String hashtagsKey;
    private final String endpointKey;

    public ModifyHandler(int onSuccessCode, boolean isGzipped, String hashtagsKey, String endpointKey) {
        this.onSuccessCode = onSuccessCode;
        this.isGzipped = isGzipped;
        this.hashtagsKey = hashtagsKey;
        this.endpointKey = endpointKey;
    }

    public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
        Iterable<String> hashtags = Splitter
                .on(",")
                .trimResults()
                .omitEmptyStrings()
                .split(request.getParameter(hashtagsKey));
        String endpoint = request.getParameter(endpointKey);

        // modify the list of hashtags subscribed to for that endpoint in the event bus
        BusManager.INSTANCE.modify(hashtags, endpoint);

        response.setStatus(onSuccessCode);
        baseRequest.setHandled(true);
    }
}
