# RabbitMQ Proxy Protocol

Material to test proxy protocol support in RabbitMQ

## Repository Cloning

```
cd /tmp
git clone https://github.com/acogoluegnes/rabbitmq-proxy-protocol.git
```

## HAProxy Setup

Download and compile HAProxy:
```
wget http://www.haproxy.org/download/1.8/src/haproxy-1.8.14.tar.gz
tar xf haproxy-1.8.14.tar.gz
cd haproxy-1.8.14
make USE_OPENSSL=1 TARGET=generic
```
Generate a private key and a certificate for TLS in HAProxy:
```
cd /tmp
git clone https://github.com/michaelklishin/tls-gen.git
cd tls-gen/basic
make
```

Concatenate the files into one for HAProxy:
```
echo '-----BEGIN DH PARAMETERS-----
MIIBCAKCAQEA//////////+t+FRYortKmq/cViAnPTzx2LnFg84tNpWp4TZBFGQz
+8yTnc4kmz75fS/jY2MMddj2gbICrsRhetPfHtXV/WVhJDP1H18GbtCFY2VVPe0a
87VXE15/V8k1mE8McODmi3fipona8+/och3xWKE2rec1MKzKT0g6eXq8CrGCsyT7
YdEIqUuyyOP7uWrat2DX9GgdT0Kj3jlN9K5W7edjcrsZCwenyO4KbXCeAvzhzffi
7MA0BM0oNC9hkXL+nOmFg/+OTxIy7vKBg8P+OxtMb61zO7X8vC7CIAXFjvGDfRaD
ssbzSibBsu/6iGtCOGEoXJf//////////wIBAg==
-----END DH PARAMETERS-----' > ffdhe2048.txt
cat server/cert.pem server/key.pem ffdhe2048.txt > /tmp/haproxy-1.8.14/haproxy-secure.txt
```

Start HAProxy:

```
/tmp/haproxy-1.8.14/haproxy -f /tmp/rabbitmq-proxy-protocol/haproxy.cfg -d
```

## RabbitMQ

Before starting the broker, enable the appropriate plugins:
```
./rabbitmq-plugins enable rabbitmq_management rabbitmq_amqp1_0 rabbitmq_mqtt rabbitmq_stomp rabbitmq_web_stomp rabbitmq_web_mqtt
```

Drop the `configuration/rabbitmq.conf` file
in the `etc/rabbitmq` directory of your RabbitMQ installation and
start the broker.

## Test scripts

There is a Groovy script to test the protocols (AMQP 0.9.1 and 1.0,
STOMP, MQTT, Web STOMP, and Web MQTT).

Install Groovy with a native package manager or with
[SDKMAN!](https://sdkman.io/), then launch a script:

```
groovy src/proxy-protocol-amqp-0-9-1.groovy
```

Each script opens connections, lists the local ports with `lsof`,
and then use the management UI to get information about these connections.
The local ports reported by the management UI and the local ports
reported by `lsof` should be the same, as the information is
forwarded by HAProxy with the proxy protocol.

Each script does this with a plain connection, a connection with
TLS termination (TLS between the client and the proxy, no
TLS between the proxy and the broker), and TLS all the way down.

The output should look like this (don't mind the warnings about security):

```
Starting 'Plain Connection'
Listing connections from management plugin...
127.0.0.1:38662 -> 127.0.0.1:5672
127.0.0.1:38666 -> 127.0.0.1:5672
127.0.0.1:38670 -> 127.0.0.1:5672
End of connection listing
Executing lsof -c java -i :5670 -a
COMMAND  PID         USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
java    8029 acogoluegnes   81u  IPv6 420028      0t0  TCP localhost:38662->localhost:5670 (ESTABLISHED)
java    8029 acogoluegnes   84u  IPv6 413432      0t0  TCP localhost:38666->localhost:5670 (ESTABLISHED)
java    8029 acogoluegnes   85u  IPv6 420032      0t0  TCP localhost:38670->localhost:5670 (ESTABLISHED)

'Plain Connection' done
Starting 'TLS Termination'
[main] WARN com.rabbitmq.client.TrustEverythingTrustManager - SECURITY ALERT: this trust manager trusts every certificate, effectively disabling peer verification. This is convenient for local development but offers no protection against man-in-the-middle attacks. Please see https://www.rabbitmq.com/ssl.html to learn more about peer certificate verification.
Listing connections from management plugin...
127.0.0.1:44990 -> 127.0.0.1:5672
127.0.0.1:44994 -> 127.0.0.1:5672
127.0.0.1:44998 -> 127.0.0.1:5672
End of connection listing
Executing lsof -c java -i :5669 -a
COMMAND  PID         USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
java    8029 acogoluegnes   81u  IPv6 420033      0t0  TCP localhost:44990->localhost:5669 (ESTABLISHED)
java    8029 acogoluegnes   84u  IPv6 420034      0t0  TCP localhost:44994->localhost:5669 (ESTABLISHED)
java    8029 acogoluegnes   85u  IPv6 420907      0t0  TCP localhost:44998->localhost:5669 (ESTABLISHED)

'TLS Termination' done
Starting 'End-to-end TLS'
[main] WARN com.rabbitmq.client.TrustEverythingTrustManager - SECURITY ALERT: this trust manager trusts every certificate, effectively disabling peer verification. This is convenient for local development but offers no protection against man-in-the-middle attacks. Please see https://www.rabbitmq.com/ssl.html to learn more about peer certificate verification.
Listing connections from management plugin...
127.0.0.1:52098 -> 127.0.0.1:5671
127.0.0.1:52102 -> 127.0.0.1:5671
127.0.0.1:52106 -> 127.0.0.1:5671
End of connection listing
Executing lsof -c java -i :5668 -a
COMMAND  PID         USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
java    8029 acogoluegnes   81u  IPv6 416069      0t0  TCP localhost:52098->localhost:5668 (ESTABLISHED)
java    8029 acogoluegnes   84u  IPv6 416070      0t0  TCP localhost:52102->localhost:5668 (ESTABLISHED)
java    8029 acogoluegnes   85u  IPv6 416071      0t0  TCP localhost:52106->localhost:5668 (ESTABLISHED)

'End-to-end TLS' done
```




