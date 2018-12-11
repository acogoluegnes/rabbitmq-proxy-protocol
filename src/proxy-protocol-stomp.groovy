import com.rabbitmq.client.TrustEverythingTrustManager
import com.rabbitmq.http.client.Client
import org.slf4j.LoggerFactory

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Grab(group = 'com.rabbitmq', module = 'amqp-client', version = "5.5.1")
@Grab(group = 'com.rabbitmq', module = 'http-client', version = "3.0.1")
@Grab(group = 'org.springframework', module = 'spring-web', version = "5.1.3.RELEASE")
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = "4.5.6")
@Grab(group = 'org.slf4j', module = 'slf4j-simple', version = '1.7.25')

def parametersPlainConnection = [
        "Plain Connection", 61612, { port -> new Socket("localhost", port)}
]

def secureSocketCreator = { port ->
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(null, [new TrustEverythingTrustManager()] as TrustManager[], null)
    sslContext.socketFactory.createSocket("localhost", port)
}

def parametersTlsTermination = [
        "TLS Termination", 61611, secureSocketCreator
]

def parametersEndToEndTls = [
        "End-to-end TLS", 61610, secureSocketCreator
]

def parameters = [
        parametersPlainConnection,
        parametersTlsTermination,
        parametersEndToEndTls
]

parameters.each { parameter ->
    def description = parameter.get(0)
    def port = parameter.get(1)
    def socketCreator = parameter.get(2)
    println "Starting '$description'"

    def sockets = new ArrayList()
    [1, 2, 3].each {
        Socket socket = socketCreator(port)
        PrintWriter writer =
                new PrintWriter(socket.getOutputStream(), true);
        writer.println("CONNECT")
        writer.println()
        writer.print("\000")
        writer.flush()
//        BufferedReader reader =
//                new BufferedReader(
//                        new InputStreamReader(socket.getInputStream()));
        sockets.add(socket)
    }

    def latch = new CountDownLatch(1)
    Client c = new Client("http://127.0.0.1:15672/api/", "guest", "guest")
    def scheduler = Executors.newScheduledThreadPool(1)
    def task = scheduler.scheduleAtFixedRate(
            { ->
                def connectionInfos = c.getConnections()
                if (connectionInfos.size() == sockets.size()) {
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

    sockets.forEach({ s -> s.close() })
    println "'$description' done"
}


