import com.rabbitmq.client.TrustEverythingTrustManager
import com.rabbitmq.http.client.Client
import org.apache.qpid.jms.JmsConnectionFactory
import org.slf4j.LoggerFactory

import javax.naming.Context
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Grab(group = 'org.apache.qpid', module = 'qpid-jms-client', version = "0.39.0")
@Grab(group = 'com.rabbitmq', module = 'amqp-client', version = "5.5.1")
@Grab(group = 'com.rabbitmq', module = 'http-client', version = "3.0.1")
@Grab(group = 'org.springframework', module = 'spring-web', version = "5.1.3.RELEASE")
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = "4.5.6")
@Grab(group = 'org.slf4j', module = 'slf4j-simple', version = '1.7.25')

def parametersPlainConnection = [
        "Plain Connection", 5670, { env ->
        env.put("connectionfactory.myFactoryLookup", "amqp://localhost:5670")
    }, { }
]

def enableTls = {cf ->
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(null, [new TrustEverythingTrustManager()] as TrustManager[], null)
    cf.setSslContext(sslContext)
}

def parametersTlsTermination = [
        "TLS Termination", 5669, { env ->
        env.put("connectionfactory.myFactoryLookup", "amqps://localhost:5669")
    }, enableTls
]

def parametersEndToEndTls = [
        "End-to-end TLS", 5668, { env ->
        env.put("connectionfactory.myFactoryLookup", "amqps://localhost:5668")
    }, enableTls
]

def parameters = [
        parametersPlainConnection, parametersTlsTermination, parametersEndToEndTls
]

parameters.each { parameter ->
    def description = parameter.get(0)
    def port = parameter.get(1)
    def envCallback = parameter.get(2)
    def connectionFactoryCallback = parameter.get(3)
    println "Starting '$description'"
    Hashtable<Object, Object> env = new Hashtable<Object, Object>()
    env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory")
    envCallback(env)

    javax.naming.Context context = new javax.naming.InitialContext(env)
    JmsConnectionFactory factory = (JmsConnectionFactory) context.lookup("myFactoryLookup")
    connectionFactoryCallback(factory)

    def connections = new ArrayList()
    [1, 2, 3].each { connections.add(factory.createConnection("guest", "guest")) }

    connections.each { c ->
        c.start()
    }

    def latch = new CountDownLatch(1)
    Client c = new Client("http://127.0.0.1:15672/api/", "guest", "guest")
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


