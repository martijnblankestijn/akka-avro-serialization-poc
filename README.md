# Avro serialization study
[![Build Status](https://circleci.com/gh/martijnblankestijn/akka-avro-serialization-poc.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/martijnblankestijn/akka-avro-serialization-poc)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

Study of using Avro as the serialization mechanism for messages and events.

## Focus
The focus was on investigating what the effort would be to disable the default Java Serializer of Akka.
I looked at two parts. 

One were the persistent events which are being persisted. 
So the application needs to deal with all the previous versions. 
I primarily looked at up-casting as the way to interpret the old event.

The second are the commands. For now I am only interested in the current version,
but this will bring some challenges.

## How to use

- start Cassandra on port 9042. I use Docker for that, see the shell-script in the root of the project.
- start the server


### Running Cassandra
If using a Mac, like I do, make sure that you can use docker.

I use docker-machine with the default image aptly called `default`. 
If it isn't running already (check with `docker-machine ls`), do so with `docker-machine start default`. 

Make sure the environment-variables of the VM on which your container will run is available.
Do this through the `eval "$(docker-machine env default)"` command.

Execute the shell script `./cassandra.sh`.


### Running the server
I just execute `nl.codestar.api.Server` from my IDE (IntelliJ),
which will use as a default the configuration for `server-1`.

You can supply a commandline argument like `nl.codestar.api.Server server-2`. 
The first commandline argument is the name of the file on the classpath that will be loaded. 

See below why it does not work with sbt yet.

## Resources

The following resources where handy.

### Serialization

- [Schema evolution in Avro, Protocol Buffers and Thrift by Martin Kleppmann](http://martin.kleppmann.com/2012/12/05/schema-evolution-in-avro-protocol-buffers-thrift.html) 

### Avro

- [Apache Avro](http://avro.apache.org/docs/1.8.2/)
- [Avro schema doesn't honor backward compatibilty](https://stackoverflow.com/questions/34733604/avro-schema-doesnt-honor-backward-compatibilty)
- [avro4s](https://github.com/sksamuel/avro4s)

### Akka

- [akka-serialization-test: Study on akka-serialization using Google Protocol Buffers, Kryo and Avro](https://github.com/dnvriend/akka-serialization-test)
- [Akka Serialization](https://doc.akka.io/docs/akka/current/serialization.html)


## Akka Persistence Events
For the events (of Akka Persistence) different versions of the same event have been made,
which will be 'up-casted' to the latest version when read. 
This way the domain logic only needs to know the latest version of an event.
The up-casting will be done with the use of defaults, defined in the Avro schema.

In the current project you see the different versions of the commands as they are kept around to test it.
In normal circumstances you would only have the Avro-schemas (in `src/main/resources/').

As a starting point, see the `AppointmentEventAvroSerializer` class.

## Akka Commands
For now the AvroCommandSerializer just uses a list of classes to serialize. 
There is no versioning possibility for the commands 

No up-casting is supported for command.
The implication is that when using Akka Persistence in an Akka cluster 
there is no deployment of new versions of commands without downtime.

New nodes will not understand the old command and vice-versa.
So for a cluster,you will also need versioning of commands or introduction of new commands.

## Monitoring
As an experiment added [Kamon](http://kamon.io/) to monitor the application.
First hick-up was a NullPointerException on version 1.0.0 for which I made an [issue](https://github.com/kamon-io/kamon-akka-remote/issues/9#issuecomment-360953598).
This was resolved quickly.

In it's current setup, it uses [Zipkin](https://zipkin.io/) and [Prometheus](https://prometheus.io/).

For Zipkin I used a docker container that can be started with `docker run -d -p 9411:9411 openzipkin/zipkin`.
To see the traces, go to [http://192.168.99.100:9411/zipkin/](http://192.168.99.100:9411/zipkin/).
The 192.168.99.100 is the ip-address of the Docker-host on my Mac.

The Prometheus Reporter of Kamon start an embedded http server on [http://0.0.0.0:9095](http://0.0.0.0:9095).
I just downloaded Prometheus, added that endpoint to the configuration file `prometheus.yml` in the root of Prometheus:

```yaml

    static_configs:
      - targets: ['localhost:9090', 'localhost:9095']
```  
The results can be queried on [http://0.0.0.0:9090/graph](http://0.0.0.0:9090/graph).


## Loose ends

### AppointmentEventAvroSerializer.toBinary

Needs to be improved, but the types do not line up.

### Running the server with `sbt` fails
When preparing this README, I the `build.sbt` so that it should be runnable with `sbt run`.
This gives the error mentioned below.

```
2017-12-30 14:03:33,641 - akka.event.EventStreamUnsubscriber -> ERROR[appointmentSystem-akka.actor.default-dispatcher-2] EventStreamUnsubscriber - swallowing exception during message send
java.lang.ClassNotFoundException: scala.Int
        at sbt.internal.inc.classpath.ClasspathFilter.loadClass(ClassLoaders.scala:74)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
        at java.lang.Class.forName0(Native Method)
        at java.lang.Class.forName(Class.java:348)
        at akka.actor.ReflectiveDynamicAccess.$anonfun$getClassFor$1(ReflectiveDynamicAccess.scala:21)
        at scala.util.Try$.apply(Try.scala:209)
        at akka.actor.ReflectiveDynamicAccess.getClassFor(ReflectiveDynamicAccess.scala:20)
        at akka.serialization.Serialization.$anonfun$bindings$3(Serialization.scala:333)
        at scala.collection.TraversableLike$WithFilter.$anonfun$map$2(TraversableLike.scala:739)
        at scala.collection.immutable.HashMap$HashMap1.foreach(HashMap.scala:231)
        at scala.collection.immutable.HashMap$HashTrieMap.foreach(HashMap.scala:462)
        at scala.collection.TraversableLike$WithFilter.map(TraversableLike.scala:738)
        at akka.serialization.Serialization.<init>(Serialization.scala:331)
```