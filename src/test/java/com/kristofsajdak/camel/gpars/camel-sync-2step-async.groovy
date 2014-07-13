package com.kristofsajdak.camel.gpars

import groovyx.gpars.dataflow.DataflowVariable
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jackson.JacksonDataFormat
import org.apache.camel.util.StopWatch
import org.apache.camel.util.URISupport

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static com.kristofsajdak.camel.gpars.CamelSync2StepAsync.*

public class CamelSync2StepAsync {

    static final json = new JacksonDataFormat()

    static def gparsContext() {

        final context = TestSupport.camelContext("gpars")
        final template = context.createProducerTemplate()
        context.addRoutes(new RouteBuilder() {
            @Override
            void configure() throws Exception {

                final pool = Executors.newScheduledThreadPool(20)

                from("jetty:http://0.0.0.0:8080/").task({ Exchange exchange ->

                    def result = new DataflowVariable<>()
                    def step1 = template.requestBodyAndHeadersAsPromise("direct:step1", exchange.in.body as String, [:])
                    step1.then({ step1Answer ->

                        def step2Closure
                        step2Closure = {

                            final step2 = template.requestBodyAndHeadersAsPromise("direct:step2", "",
                                    [(Exchange.HTTP_QUERY): constant("ticket_id=" + step1Answer.ticket_id)])

                            step2.then({ correlatedAnswer ->
                                if (correlatedAnswer.response != null) {
                                    result << correlatedAnswer
                                } else {
                                    pool.schedule(step2Closure as Runnable, 1, TimeUnit.SECONDS)
                                }
                            })
                        }

                        step2Closure()

                    })

                    return result

                }).marshal(json)

                from("direct:step1").to("ahc:http://0.0.0.0:8088/step1").unmarshal(json)
                from("direct:step2").to("ahc:http://0.0.0.0:8088/step2").unmarshal(json)
            }


        })
        context
    }


    static def puredslContext() {

        final context = TestSupport.camelContext("puredsl")
        final template = context.createProducerTemplate()
        context.addRoutes(new RouteBuilder() {
            @Override
            void configure() throws Exception {

                from("jetty:http://0.0.0.0:8080/")
                        .to("ahc:http://0.0.0.0:8088/step1?bridgeEndpoint=true").unmarshal(json)
                        .setHeader(Exchange.HTTP_QUERY, { "ticket_id=${it.in.body.ticket_id}" })
                        .loop(30)
                            .delay(500)
                            .setBody(constant(""))
                            .to("ahc:http://0.0.0.0:8088/step2?bridgeEndpoint=true").unmarshal(json)
                                .choice()
                                    .when({ ex -> ex.in.body.response != null }).marshal(json).stop()

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
                    if (times < 3) {
                        // fake 404 3 times
                        times++
                        println("in step2 endpoint")
                        exchange.out.body = [:]
                    } else {
                        // pass back a response for the requested ticketId the 4th time
                        exchange.out.body = [response: "foobar $ticketId".toString()]
                    }

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

//print singleRequest(gparsContext())
print singleRequest(puredslContext())

mocksContext.stop()

System.exit(0)



