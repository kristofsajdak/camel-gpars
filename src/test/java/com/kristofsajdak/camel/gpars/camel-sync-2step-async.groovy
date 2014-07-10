package com.kristofsajdak.camel.gpars

import groovyx.gpars.dataflow.DataflowVariable
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jackson.JacksonDataFormat
import org.apache.camel.util.StopWatch
import org.apache.camel.util.URISupport

import static com.kristofsajdak.camel.gpars.CamelSync2StepAsync.*

public class CamelSync2StepAsync {

    static final json = new JacksonDataFormat()

    static def gparsContext() {

        final context = TestSupport.camelContext("gpars")
        final template = context.createProducerTemplate()
        context.addRoutes(new RouteBuilder() {
            @Override
            void configure() throws Exception {
                from("jetty:http://0.0.0.0:8080/").task({ Exchange exchange ->

                    def result = new DataflowVariable<>()
                    def step1 = template.requestBodyAndHeadersAsPromise("direct:step1", exchange.in.body as String, [:])
                    step1.then({ ticketId ->
                        println "ticketId: $ticketId"

                        def step2Closure
                        step2Closure = {
                            final step2 = template.requestBodyAndHeadersAsPromise("direct:step2", "", [(Exchange.HTTP_QUERY): constant("ticket_id=" + ticketId)])
                            step2.then({ String correlatedAnswer ->
                                result << correlatedAnswer
                            }, { e ->
                                Thread.sleep(1000)
                                step2Closure()
                            })
                        }

                        step2Closure()

                    })

                    result
                })

                from("direct:step1").to("ahc:http://0.0.0.0:8088/step1").unmarshal(json)
                from("direct:step2").to("ahc:http://0.0.0.0:8088/step2").unmarshal(json)
            }


        })
        context
    }


    static def mocksContext() {


        final context = TestSupport.camelContext("mocks")
        context.addRoutes(new RouteBuilder() {

            static def Map<String, Object> parseUriQueryParams(Exchange exchange) {
                URISupport.parseQuery(exchange.in.headers.CamelHttpQuery as String)
            }

            @Override
            void configure() throws Exception {

                int times = 0

                from("jetty:http://0.0.0.0:8088/step1").process { Exchange exchange ->
                    final reqContent = exchange.in.body
                    // noop on content for simplicity of example
                    // just hand back a correlation ticket
                    exchange.out.body = [ticket_id: UUID.randomUUID().toString()]
                }.marshal(json)

                from("jetty:http://0.0.0.0:8088/step2").process { Exchange exchange ->
                    final ticketId = parseUriQueryParams(exchange).ticket_id
                    /*if (times<=3) {
                        times++
                        exchange.out.body = ""
                        exchange.out.setHeader(Exchange.HTTP_RESPONSE_CODE, 404)
                    } else {*/
                        exchange.out.body = [response: "foobar"]
                    //}

                }.marshal(json)
            }
        })
        context
    }

    static def singleRequest(camelContext) {
        camelContext.start()
        final template = camelContext.createProducerTemplate()
        StopWatch watch = new StopWatch()
        watch.restart()
        final result = template.requestBody("ahc:http://0.0.0.0:8080/", "blah", String.class)
        watch.stop()
        camelContext.stop()
        "result: $result taken: ${watch.taken()}"
    }

}

final mocksContext = mocksContext()
mocksContext.start()

print singleRequest(gparsContext())

mocksContext.stop()

System.exit(0)



