package com.cmenguy.monitor.hashtags.server;

import com.cmenguy.monitor.hashtags.server.api.ContextManager;
import com.cmenguy.monitor.hashtags.server.api.SafeHttpClient;
import com.cmenguy.monitor.hashtags.server.core.BusManager;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class HashTagProducer {
    private final static Logger logger = LoggerFactory.getLogger(HashTagProducer.class);

    public void run(Config config, int port) {
        try {
            Server server = new Server(port);

            int numWorkers = config.getInt("Server.numWorkers");
            int maxQueueSize = config.getInt("Server.maxQueueSize");
            int maxRetries = config.getInt("Server.maxRetries");
            BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<Runnable>(maxQueueSize);
            // run in calling thread if queue is full
            RejectedExecutionHandler rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
            ExecutorService executorService =  new ThreadPoolExecutor(numWorkers, numWorkers,
                    0, TimeUnit.MILLISECONDS, blockingQueue, rejectedExecutionHandler);
            SafeHttpClient httpClient = new SafeHttpClient(numWorkers, numWorkers, maxRetries);
            BusManager.INSTANCE
                    .withExecutor(executorService)
                    .withHttpClient(httpClient);

            ContextManager manager = new ContextManager();
            server.setHandler(manager.setup(config));

            server.start();
            server.join();
        } catch (Exception e) {
            logger.error("unable to start server on port " + port, e);
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("server name needs to be specified as parameter");
        }
        String serverName = args[0];
        Config config = ConfigFactory.load(); // loads from -Dconfig.file=...
        int port = config.getInt("Server.Topology." + serverName + ".port");
        new HashTagProducer().run(config, port);
    }
}
