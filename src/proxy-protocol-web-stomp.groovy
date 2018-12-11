import com.rabbitmq.client.TrustEverythingTrustManager
import com.rabbitmq.http.client.Client
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.LoggerFactory

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Grab(group = 'org.java-websocket', module = 'Java-WebSocket', version = "1.3.9")
@Grab(group = 'com.rabbitmq', module = 'amqp-client', version = "5.5.1")
@Grab(group = 'com.rabbitmq', module = 'http-client', version = "3.0.1")
@Grab(group = 'org.springframework', module = 'spring-web', version = "5.1.3.RELEASE")
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = "4.5.6")
@Grab(group = 'org.slf4j', module = 'slf4j-simple', version = '1.7.25')

def parametersPlainConnection = [
        "Plain Connection", 15670, "ws://localhost:15670/ws", { }
]

def enableTls = { client ->
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(null, [new TrustEverythingTrustManager()] as TrustManager[], null)
    socket = sslContext.socketFactory.createSocket()
    client.setSocket(socket)
}

def parametersTlsTermination = [
        "TLS Termination", 15669, "wss://localhost:15669/ws", enableTls
]

def parametersEndToEndTls = [
        "End-to-end TLS", 15668, "wss://localhost:15668/ws", enableTls
]

def parameters = [
        parametersPlainConnection,
        parametersTlsTermination,
        parametersEndToEndTls
]

parameters.each { parameter ->
    def description = parameter.get(0)
    def port = parameter.get(1)
    def url = parameter.get(2)
    def clientCallback = parameter.get(3)
    println "Starting '$description'"

    def clients = new ArrayList()
    [1, 2, 3].each {
        CountDownLatch latch = new CountDownLatch(1);
        WebSocketClient client = new WebSocketClient(new URI(url)) {
            @Override
            void onOpen(ServerHandshake serverHandshake) {

            }

            @Override
            void onMessage(String s) {
                if (s.contains("CONNECTED")) {
                    latch.countDown()
                }
            }

            @Override
            void onClose(int i, String s, boolean b) { }

            @Override
            void onError(Exception e) {
                e.printStackTrace();
            }
        }
        clientCallback(client)
        client.connectBlocking()
        client.send("CONNECT\nlogin: guest\npasscode: guest\n\n\u0000")
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Could not connect in 5 seconds")
        }
        clients.add(client)
    }

    def latch = new CountDownLatch(1)
    Client c = new Client("http://127.0.0.1:15672/api/", "guest", "guest")
    def scheduler = Executors.newScheduledThreadPool(1)
    def task = scheduler.scheduleAtFixedRate(
            { ->
                def connectionInfos = c.getConnections()
                if (connectionInfos.size() == clients.size()) {
                    println "Listing connections from management plugin..."
                    c.getConnections().forEach({ info -> println info.getName() })
                    println "End of connection listing"
                    latch.countDown()
                }

            },
            1, 3, TimeUnit.SECONDS
    )

    def completed = latch.await(10, TimeUnit.SECONDS)
    if (!completed) {
        LoggerFactory.getLogger("rabbitmq").warn("Timeout reached, could not list connections")
    }
    task.cancel(true)
    scheduler.shutdown()

    def command = "lsof -c java -i :$port -a"
    println "Executing $command"
    def sout = new StringBuilder(), serr = new StringBuilder()
    def proc = command.execute()
    proc.consumeProcessOutput(sout, serr)
    proc.waitForOrKill(1000)
    println "$sout"

    clients.forEach({ client -> client.closeBlocking() })
    println "'$description' done"
}


