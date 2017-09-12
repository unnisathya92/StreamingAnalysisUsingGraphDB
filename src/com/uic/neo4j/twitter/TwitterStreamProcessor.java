package com.uic.neo4j.twitter;

import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.BasicClient;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static java.lang.System.getenv;
import static java.util.Arrays.asList;

public class TwitterStreamProcessor {

    private static final int BATCH = 1;
static String APIKEY = "gkYX2bJ3hMsJYia0QbgWDRsdk:rippoqjD5ueQgUFM7zVz8MTK5wKHlzrM19DNQv5jQbB8aWHzHo:468294113-LGqfC8xkc4m13bfrCprRWjVYaS1HOA5ZNPiWKBWx:8ZK1J3WP8CSPUJIwYZfoIiDZuJoiZFTnN4MxzZUqzELcJ";
    public static void main(String... args) throws InterruptedException, MalformedURLException, URISyntaxException {
        int maxReads = 1000000;

        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(10000);
        List<Long> userIds = asList();
        String searchTerms = "Oviya";
        List<String> terms = asList(searchTerms.split(","));
      
        BasicClient client = configureStreamClient(msgQueue,APIKEY , userIds, terms);
        TwitterNeo4jWriter writer = new TwitterNeo4jWriter("http://localhost:7474");
        writer.init();
        int numProcessingThreads = Math.max(1,Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService service = Executors.newFixedThreadPool(numProcessingThreads);

        client.connect();

        List<String> buffer = new ArrayList<>(BATCH);
        for (int msgRead = 0; msgRead < maxReads; msgRead++) {
            if (client.isDone()) {
                System.err.println("Client connection closed unexpectedly: " + client.getExitEvent().getMessage());
                break;
            }
            String msg = msgQueue.poll(10, TimeUnit.SECONDS);
            System.out.println(msg);
            if (msg == null) System.out.println("Did not receive a message in 10 seconds");
            else buffer.add(msg);

            if (buffer.size() < BATCH) continue;

            List<String> tweets = buffer;
            service.submit(() -> writer.insert(tweets,3));
            buffer = new ArrayList<>(BATCH);
        }


        client.stop();
        writer.close();
    }

    private static BasicClient configureStreamClient(BlockingQueue<String> msgQueue, String twitterKeys, List<Long> userIds, List<String> terms) {
        Hosts hosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint endpoint = new StatusesFilterEndpoint()
                .followings(userIds)
                .trackTerms(terms);
        endpoint.stallWarnings(false);

        String[] keys = twitterKeys.split(":");
        Authentication auth = new OAuth1(keys[0], keys[1], keys[2], keys[3]);

        ClientBuilder builder = new ClientBuilder()
                .name("Neo4j-Twitter-Stream")
                .hosts(hosts)
                .authentication(auth)
                .endpoint(endpoint)
                .processor(new StringDelimitedProcessor(msgQueue));

        return builder.build();
    }
}
