package com.kristofsajdak.camel.gpars

import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jackson.JacksonDataFormat
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy
import org.apache.camel.util.StopWatch
import org.apache.camel.util.URISupport

import static com.kristofsajdak.camel.gpars.CamelQuote.*
import static groovyx.gpars.dataflow.Dataflow.whenAllBound

public class CamelQuote {

    static final json = new JacksonDataFormat()

    static def quoteGparsContext() {

        final context = TestSupport.camelContext("gpars")
        final template = context.createProducerTemplate()
        context.addRoutes(new RouteBuilder() {
            @Override
            void configure() throws Exception {
                from("jetty:http://0.0.0.0:8080/quote").task({ Exchange exchange ->

                    // copy the http query parameters (product-id) to the headers sent with the subsequent price and discount service calls
                    final headers = [(Exchange.HTTP_QUERY): exchange.in.headers[Exchange.HTTP_QUERY]]
                    whenAllBound(
                        // register both service calls as promises in the whenAllBound function
                        template.requestBodyAndHeadersAsPromise("direct:discounts", "", headers),
                        template.requestBodyAndHeadersAsPromise("direct:prices", "", headers),
                        // implement closure with named references to registered service call promises
                        { Map discountResolved, Map priceResolved ->
                            priceResolved.price * (1 - (discountResolved.discount / 100))
                        }
                    )
                })

                from("direct:discounts").to("ahc:http://0.0.0.0:8087/discounts").unmarshal(json)
                from("direct:prices").to("ahc:http://0.0.0.0:8088/prices").unmarshal(json)
            }
        })
        context
    }

    static def quotePuredslContext() {

        final context = TestSupport.camelContext("puredsl")

        context.addRoutes(new RouteBuilder() {
            @Override
            void configure() throws Exception {

                from("jetty:http://0.0.0.0:8080/quote")
                    .setHeader(Exchange.HTTP_QUERY, { Exchange exchange -> exchange.in.headers[Exchange.HTTP_QUERY] })
                // multicast with parallel processing and a GroupedExchange aggregation strategy
                // this will add all multicast exchanges to a list which is set as the body on the aggregated exchange
                    .multicast(new GroupedExchangeAggregationStrategy()).parallelProcessing()
                    .to("direct:discounts", "direct:prices")
                    .end()
                    .process(
                    { Exchange exchange ->
                        // get the list which contains the multicast exchanges
                        final groupedExchange = exchange.getProperty(Exchange.GROUPED_EXCHANGE) as Collection<Exchange>
                        // find the price using the serviceType header
                        final price = groupedExchange.find({ it.in.headers.serviceType == "price" }).in.body.price
                        // find the discount using the serviceType header
                        final discount = groupedExchange.find({ it.in.headers.serviceType == "discount" }).in.body.discount
                        final amount = price * (1 - (discount / 100))
                        exchange.out.body = amount
                    })

                // set serviceType header for each call on Exchange
                from("direct:discounts").to("ahc:http://0.0.0.0:8087/discounts?bridgeEndpoint=true").unmarshal(json).setHeader("serviceType", constant("discount"))
                from("direct:prices").to("ahc:http://0.0.0.0:8088/prices?bridgeEndpoint=true").unmarshal(json).setHeader("serviceType", constant("price"))


            }
        })
        context
    }

    static def quoteMocksContext(int sleep) {

        final context = TestSupport.camelContext("mocks")
        context.addRoutes(new RouteBuilder() {

            static def Map<String, Object> parseUriQueryParams(Exchange exchange) {
                URISupport.parseQuery(exchange.in.headers.CamelHttpQuery as String)
            }

            @Override
            void configure() throws Exception {

                final discounts = [1: 20, 3: 30, 6: 10]

                from("jetty:http://0.0.0.0:8087/discounts").process { Exchange exchange ->
                    final productId = parseUriQueryParams(exchange)["product-id"]
                    exchange.in.body = [productId: productId, discount: discounts.get(productId as Integer) ?: 0]
                    Thread.sleep(sleep)

                }.marshal(json)

                final prices = [1: 90, 2: 44, 3: 180, 4: 88, 5: 675, 6: 100]

                from("jetty:http://0.0.0.0:8088/prices").process { Exchange exchange ->
                    final productId = parseUriQueryParams(exchange)["product-id"]
                    exchange.in.body = [productId: productId, price: prices.get(productId as Integer)]
                    Thread.sleep(sleep)
                }.marshal(json)
            }
        })
        context
    }

    static def singleRequest(camelContext, productId) {
        camelContext.start()
        final template = camelContext.createProducerTemplate()
        StopWatch watch = new StopWatch()
        watch.restart()
        final result = template.requestBody("ahc:http://0.0.0.0:8080/quote?product-id=$productId", "", String.class)
        watch.stop()
        camelContext.stop()
        "result: $result taken: ${watch.taken()}"
    }

}

final mocksContext = quoteMocksContext(800)
mocksContext.start()

singleRequest(quotePuredslContext(), 3) // warm up
singleRequest(quoteGparsContext(), 3) // warm up

final puredslResult = "puredsl ${singleRequest(quotePuredslContext(), 3)}"
final gparsResult = "gpars ${singleRequest(quoteGparsContext(), 3)}"

mocksContext.stop()

println "$puredslResult $gparsResult"

System.exit(0)



