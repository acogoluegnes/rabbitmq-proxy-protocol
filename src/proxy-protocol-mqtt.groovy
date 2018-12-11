import com.rabbitmq.client.TrustEverythingTrustManager
import com.rabbitmq.http.client.Client
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Grab(group = 'org.eclipse.paho', module = 'org.eclipse.paho.client.mqttv3', version = "1.1.0")
@Grab(group = 'com.rabbitmq', module = 'amqp-client', version = "5.5.1")
@Grab(group = 'com.rabbitmq', module = 'http-client', version = "3.0.1")
@Grab(group = 'org.springframework', module = 'spring-web', version = "5.1.3.RELEASE")
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = "4.5.6")
@Grab(group = 'org.slf4j', module = 'slf4j-simple', version = '1.7.25')

def parametersPlainConnection = [
        "Plain Connection", 1882, "tcp://localhost:1882", {}
]

def enableTls = { options ->
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(null, [new TrustEverythingTrustManager()] as TrustManager[], null)
    options.setSocketFactory(sslContext.socketFactory)
}

def parametersTlsTermination = [
        "TLS Termination", 8882, "ssl://localhost:8882", enableTls
]

def parametersEndToEndTls = [
        "End-to-end TLS", 8881, "ssl://localhost:8881", enableTls
]

def parameters = [
        parametersPlainConnection, parametersTlsTermination,
        parametersEndToEndTls
]

parameters.each { parameter ->
    def description = parameter.get(0)
    def port = parameter.get(1)
    def url = parameter.get(2)
    def optionsCallback = parameter.get(3)
    println "Starting '$description'"

    MqttConnectOptions connOpts = new MqttConnectOptions()
    connOpts.setCleanSession(true)
    MemoryPersistence persistence = new MemoryPersistence()
    connOpts.setUserName("guest")
    connOpts.setPassword("guest".toCharArray())
    optionsCallback(connOpts)

    def clients = new ArrayList()
    [1, 2, 3].each {
        MqttClient client = new MqttClient(url, UUID.randomUUID().toString(), persistence)
        client.connect(connOpts)
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

    clients.forEach({ client -> client.disconnect() })
    println "'$description' done"
}


