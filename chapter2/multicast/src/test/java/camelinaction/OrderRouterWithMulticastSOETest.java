package camelinaction;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class OrderRouterWithMulticastSOETest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // create CamelContext
        CamelContext camelContext = super.createCamelContext();
        
        // connect to embedded ActiveMQ JMS broker
        ConnectionFactory connectionFactory = 
            new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("jms",
            JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));
        
        return camelContext;
    }
    
    @Test
    public void testPlacingOrders() throws Exception {
        getMockEndpoint("mock:accounting_before_exception").expectedMessageCount(1);
        getMockEndpoint("mock:accounting").expectedMessageCount(0);
        getMockEndpoint("mock:production").expectedMessageCount(0);
        assertMockEndpointsSatisfied();
    }

    /**
     * Take care when using stopOnException with asynchronous messaging.
     * In our example, the exception could have happened after the message had been
     * consumed by both the accounting and production queues, nullifying the
     * stopOnException effect. In our test case, we decided to use synchronous direct
     * endpoints, which would allow us to test this feature of the multicast
     *
     * 在异步消息传递中, 如果错误发生在队列处理之后, stopOnException将无效, 这里使用的是同步端点方式
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // load file orders from src/data into the JMS queue
                from("file:src/data?noop=true").to("jms:incomingOrders");
        
                // content-based router
                from("jms:incomingOrders")
                    .choice()
                        .when(header("CamelFileName").endsWith(".xml"))
                            .to("jms:xmlOrders")  
                        .when(header("CamelFileName").regex("^.*(csv|csl)$"))
                            .to("jms:csvOrders")
                        .otherwise()
                            .to("jms:badOrders");        
                
                from("jms:xmlOrders")
                    .multicast()
                    .stopOnException() // 此功能将在捕获到的第⼀个异常时停⽌多播
                    .to("jms:accounting", "jms:production");
               
                from("jms:accounting")      
                    .to("mock:accounting_before_exception")
                    .throwException(Exception.class, "I failed!")
                    .log("Accounting received order: ${header.CamelFileName}")
                    .to("mock:accounting");                
                
                from("jms:production")        
                    .log("Production received order: ${header.CamelFileName}")
                    .to("mock:production");                
            }
        };
    }
}
