import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.http.client.Client
import org.slf4j.LoggerFactory

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Grab(group = 'com.rabbitmq', module = 'amqp-client', version = "5.5.1")
@Grab(group = 'com.rabbitmq', module = 'http-client', version = "3.0.1")
@Grab(group = 'org.springframework', module = 'spring-web', version = "5.1.3.RELEASE")
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = "4.5.6")
@Grab(group = 'org.slf4j', module = 'slf4j-simple', version = '1.7.25')

def parametersPlainConnection = [
        "Plain Connection", { cf ->
    cf.setPort(5670)
}
]
def parametersTlsTermination = [
        "TLS Termination", { cf ->
    cf.useSslProtocol()
    cf.setPort(5669)
}
]
def parametersEndToEndTls = [
        "End-to-end TLS", { cf ->
    cf.useSslProtocol()
    cf.setPort(5668)
}
]

def parameters = [parametersPlainConnection, parametersTlsTermination, parametersEndToEndTls]

parameters.each { parameter ->
    def description = parameter.get(0)
    def connectionFactoryCallback = parameter.get(1)
    println "Starting '$description'"
    ConnectionFactory cf = new ConnectionFactory()
    connectionFactoryCallback(cf)
    def port = cf.getPort()

    def connections = new ArrayList()
    [1, 2, 3].each { connections.add(cf.newConnection()) }

    def latch = new CountDownLatch(1)
    Client c = new Client("http://127.0.0.1:15672/api/", "guest", "guest");
    def scheduler = Executors.newScheduledThreadPool(1)
    def task = scheduler.scheduleAtFixedRate(
            { ->
                def connectionInfos = c.getConnections()
                if (connectionInfos.size() == connections.size()) {
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

    connections.forEach({ connection -> connection.close() })
    println "'$description' done"
}

