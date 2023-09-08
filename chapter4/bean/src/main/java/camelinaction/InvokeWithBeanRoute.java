package camelinaction;

import org.apache.camel.builder.RouteBuilder;

/**
 * Using a bean in the route to invoke HelloBean.
 */
public class InvokeWithBeanRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:hello")
                // .to("bean:HelloBean?method=hello")
            // instantiate HelloBean once, and reuse and invoke the hello bean
            // org.apache.camel.component.bean.BeanProcessor 实现服务器激活模式
            .bean(HelloBean.class, "hello");
    }
}
