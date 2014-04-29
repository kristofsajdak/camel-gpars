package com.kristofsajdak.camel.gpars

import groovyx.gpars.dataflow.Promise
import groovyx.gpars.dataflow.Select
import groovyx.gpars.dataflow.SelectResult
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jackson.JacksonDataFormat
import org.apache.camel.util.StopWatch
import org.apache.log4j.Logger

import static com.kristofsajdak.camel.gpars.CamelQuoteSelect.quoteGparsContext
import static com.kristofsajdak.camel.gpars.CamelQuoteSelect.quoteMock
import static groovyx.gpars.dataflow.Dataflow.select
import static groovyx.gpars.dataflow.Dataflow.whenAllBound

public class CamelQuoteSelect {

    static logger = Logger.getLogger(CamelQuoteSelect.class)

    static final json = new JacksonDataFormat()

    static def quoteGparsContext(int timeout) {

        final context = TestSupport.camelContext("gpars")
        final template = context.createProducerTemplate()

        context.addRoutes(new RouteBuilder() {
            @Override
            void configure() throws Exception {
                from("direct:lowest").task({ Exchange exchange ->

                    final promises = [
                        template.requestBodyAsPromise("direct:quote1", ""),
                        template.requestBodyAsPromise("direct:quote2", ""),
                        template.requestBodyAsPromise("direct:quote3", "")]

                    // whenAllBound returns a promise
                    final bound = whenAllBound(promises, { List quotes -> /* noop */ })

                    // compose the whenAllBound promise within a select with an additional timeout promise
                    // when one of both promises gets bound the then handler is invoked
                    select(bound, Select.createTimeout(timeout)).selectToPromise().then {
                        // find the bound promises and get the value
                        final boundValues = promises.findResults { p -> p.isBound() ? p.get() : null }
                        boundValues.each {v -> logger.info "partial quote result: $v"}
                        // get the lowest quote from all values
                        boundValues.inject(null) { acc, val -> (acc == null || val.price < acc) ? val.price : acc }
                    }

                })

                from("direct:quote1").to("ahc:http://0.0.0.0:8080/quote").unmarshal(json)
                from("direct:quote2").to("ahc:http://0.0.0.0:8081/quote").unmarshal(json)
                from("direct:quote3").to("ahc:http://0.0.0.0:8082/quote").unmarshal(json)
            }
        })
        context.addRoutes(quoteMock(8080, 1000))
        context.addRoutes(quoteMock(8081, 2000))
        context.addRoutes(quoteMock(8082, 3000))
        context
    }

    static def quoteMock(int port, int sleep) {

        new RouteBuilder() {

            @Override
            void configure() throws Exception {
                from("jetty:http://0.0.0.0:$port/quote").process { Exchange exchange ->
                    final price = new Random().nextInt(100)
                    exchange.in.body = [price: price]
                    Thread.sleep(sleep)
                }.marshal(json)
            }
        }
    }
}

final context = quoteGparsContext(2200)
context.start()
final template = context.createProducerTemplate()
StopWatch watch = new StopWatch()
watch.restart()
final result = template.requestBody("direct:lowest", "", String.class)
watch.stop()
context.stop()
println "price: $result taken: ${watch.taken()}"

System.exit(0)



