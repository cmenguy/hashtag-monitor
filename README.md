# Background
[![Build Status](https://travis-ci.org/cmenguy/hashtag-monitor.svg?branch=master)](https://travis-ci.org/cmenguy/hashtag-monitor.svg?branch=master)

This project is a distributed system used to keep tweet objects in memory broken down by hashtag.
It also allows clients to subscribe to a particular set of hashtags and will deliver the objects to any
endpoint specified by the client.

# Architecture

This system is built around a master-slave architecture. There are several reasons for this:
* The Twitter streaming API only allows 1 connection per account. So we can't have a pure P2P architecture where
every node would be able to stream from Twitter.
* Clients have no knowledge of which node contains which hashtag, so the master node can be used to route the traffic
to the appropriate nodes.

## Protocol

This particular use case has been built around the use of protobuf as the protocol for data transmission. There are
several reasons for that:
* Protocol Buffers have a very efficient serialization mechanism - this helps significantly reduce the memory footprint
of the data.
* They are also easy to extend, so if the Twitter schema was to evolve we could easily modify our protobuf schema
without breaking anything.
* They have bindings in Java which can be created from a compiled proto schema and then used easily in a Java application.

All the data is transmitted over the wire in the form of HTTP POST requests.
I initially considered compressing the protobuf-serialized byte arrays with gzip before transmitting, but it seems like
in some cases this makes the number of bytes bigger, so in the end I just use pure protobuf-serialized transmission.

## Master

The master is responsible for the following:
* Get a stream of tweets from the official Twitter streaming API.
* Transform each tweet into a protobuf object.
* Store the objects into an internal queue.
* Have a set of threads consuming from that queue, and for each object:
    * Transform each object into  protobuf-serialized byte array.
    * For each object, get the list of hashtags.
    * For each hashtag, hash it and do a `hash % numberOfSlaves` to figure out to which slave this tweet should be sent.
    * In case a tweet has multiple hashtags, it is possible to have the same tweet object sent to multiple slaves.
    * Send the data to the appropriate slave via a HTTP POST request, whose body contains the serialized protobuf object,
and as request parameter the hashtag.

A quick note on data distribution: in the interest of time, for the current approach I have used a statically defined
cluster topology. Whenever the master starts, it reads the topology from the configuration, and will use that to
distribute the tweet objects by computing a hash of each hashtag and figuring out which slave the data should be sent
to by computing the modulus based on the number of nodes in the cluster. An obvious problem with this approach is that
in order to scale up we need to restart the cluster and modify the topology, or suffer the heavy cost of a cluster 
rebalancing. One way to fix this would be to use something like consistent hashing (similar to what Cassandra does
with virtual nodes) so that we would only need to reshuffle a small number of objects when scaling up.

## Slaves

Each slave will be responsible for handling a portion of the data. Since the hash has a good randomness property, we
can expect the data to be more or less evenly distributed. The responsibilities of the slaves are the following:
* Have a Jetty HTTP server listening on a given port for incoming requests from both the master and clients.
* If a master request comes in, take its hashtag from the request parameters, and the tweet object in serialized form
from the body. At that point, post it so that any client who had subscribed to that topic will receive its own HTTP POST
request.
* If a client request comes in, check the type. If it's register, add a listener for that particular hashtag which would
be triggered automatically when posting. If it's deregister, similarly. And for modify, we just compute the difference
between what is registered and what the new list of hashtags is, and deregister/register appropriately.

It is worth noting that the posting piece is done asynchronously so that we don't wait for that before acknowledging
receipt of the tweet to the master. A pool of workers is involved to make sure this scales.

## Clients

Clients can subscribe to a given slave and ask it to forward tweet objects to a particular endpoint.
Clients just need to send an HTTP GET request to a given slave endpoint to ask for it.

For example, if we have Server1 listening at localhost:3300, then we want to subscribe to hashtags "hello" and "world"
and have the tweet objects corresponding to those redirected to an HTTP server http://myserver.com/tweets
To register, all you need to do is simply send a GET request to **http://localhost:3300/register?hashtags=hello,world&endpoint=http://myserver.com/tweets**.

Similarly, for deregistration and modification, the endpoints are respectively **/deregister** and **/modify** and they
expect the same request parameters.


# Runbook

There are 3 configurations for this system:
* **server.conf**: Properties related to the slaves.
* **master.conf**: Properties related to the master.
* **protocol.conf**: Properties related to the protocol between master/slave/client.
* **main.conf**: General config including all the previous configs, and possibly overriding some properties. This is 
what should be used when running either the master or slaves.

Before anything you will need to update **main.conf** and enter your own credentials for the Twitter API. The ones
included have voluntarily been left blank.

To build the project:

    git clone git@github.com:cmenguy/hashtag-monitor.git
    mvn package
    
This will produce a fat jar under the root of the project under `$ROOT/target/hashtag-monitor-1.0-SNAPSHOT-jar-with-dependencies.jar`

To start the master process, run the following command in the root of the project:

    java -Dorg.slf4j.simpleLogger.dateTimeFormat="yyyy-MM-dd'T'HH:mm:ss.SSSZ" -Dorg.slf4j.simpleLogger.showDateTime=true -Dconfig.file=conf/main.conf -cp target/hashtag-monitor-1.0-SNAPSHOT-jar-with-dependencies.jar com.cmenguy.monitor.hashtags.server.HashTagMaster

To start one slave process, run the following command in the root of the project.
It takes a single argument which is the name of the slave node, and it has to match one of the namespaces in
**server.conf** under **Server.Topology**. So for example to start the first one you would pass "Server1":

    java -Dorg.slf4j.simpleLogger.dateTimeFormat="yyyy-MM-dd'T'HH:mm:ss.SSSZ" -Dorg.slf4j.simpleLogger.showDateTime=true -Dconfig.file=conf/main.conf -cp target/hashg-monitor-1.0-SNAPSHOT-jar-with-dependencies.jar com.cmenguy.monitor.hashtags.server.HashTagProducer Server1

On the client side, it is left up to the experimenter to have their own HTTP server. The following are required:
* The HTTP server should accept POST requests.
* The client should be able to deserialize the protobuf-encoded byte array coming in the HTTP request. For that purpose
they can use the jar provided in this project and use the class under `com.cmenguy.monitor.hashtags.common.Twitter` and
for example do `Twitter.Tweet.parseFrom(myByteArray);`.

To register/deregister/modify hashtag subscriptions, like explained earlier, all that is needed is GET requests, so you
can simply hit a slave endpoint in your browser for example.

# Limitations & Future Work

There are several limitations/shortcuts in this system that are worth noting:
* Like said earlier, slave cluster topology is static and limits scaling up. Consistent hashing would help.
* Protobuf is very efficient, but it would be interesting to benchmark and compare against other serialization
frameworks.
* In the current setup, things are distributed by hashtag. But this could be a problem if we have a very popular
hashtag at some point, where all the traffic would end up going to the same node.
* No replication currently. One way to fix it could be that for a replication factor of N, when the master sends the
data to the relevant node, it could also send it to the next N-1 nodes as defined in the topology array.
* Everything is in memory, but it would be interesting to have things spill to disk if memory gets full instead of
using a FIFO approach for evicting data.