package com.kristofsajdak.camel.gpars

import groovyx.gpars.dataflow.DataflowVariable
import org.apache.camel.Endpoint
import org.apache.camel.Exchange
import org.apache.camel.ProducerTemplate
import org.apache.camel.impl.DefaultExchange
import org.apache.camel.support.SynchronizationAdapter

class CamelProducerGparsExtension {

    public static DataflowVariable requestBodyAsPromise(ProducerTemplate template, Endpoint endpoint) {
        requestBodyAndHeadersAsPromise(template, endpoint, "", null, null)
    }

    public static DataflowVariable requestBodyAsPromise(ProducerTemplate template, String endpoint) {
        requestBodyAndHeadersAsPromise(template, template.getCamelContext().getEndpoint(endpoint), "", null, null)
    }

    public static DataflowVariable requestBodyAsPromise(ProducerTemplate template, String endpoint, Class type) {
        requestBodyAndHeadersAsPromise(template, template.getCamelContext().getEndpoint(endpoint), "", null, type)
    }

    public static DataflowVariable requestBodyAsPromise(ProducerTemplate template, String endpoint, String body) {
        requestBodyAndHeadersAsPromise(template, template.getCamelContext().getEndpoint(endpoint), body, null, null)
    }

    public static DataflowVariable requestBodyAsPromise(ProducerTemplate template, String endpoint, String body, Class type) {
        requestBodyAndHeadersAsPromise(template, template.getCamelContext().getEndpoint(endpoint), body, null, type)
    }

    public static DataflowVariable requestBodyAsPromise(ProducerTemplate template, Endpoint endpoint, String body, Class type) {
        requestBodyAndHeadersAsPromise(template, endpoint, body, null, type)
    }

    public static DataflowVariable requestBodyAndHeadersAsPromise(ProducerTemplate template, String endpoint, String body, Map<String, Object> headers) {
        requestBodyAndHeadersAsPromise(template, template.getCamelContext().getEndpoint(endpoint), body, headers, null)
    }

    public static DataflowVariable requestBodyAndHeadersAsPromise(ProducerTemplate template, String endpoint, Map<String, Object> headers) {
        requestBodyAndHeadersAsPromise(template, template.getCamelContext().getEndpoint(endpoint), null, headers, null)
    }

    public static DataflowVariable requestBodyAndHeadersAsPromise(ProducerTemplate template, String endpoint, String body, Map<String, Object> headers, Class type) {
        requestBodyAndHeadersAsPromise(template, template.getCamelContext().getEndpoint(endpoint), body, headers, type)
    }

    public static DataflowVariable requestBodyAndHeadersAsPromise(ProducerTemplate template, Endpoint endpoint, String body, Map<String, Object> headers, Class type) {
        final DataflowVariable result = new DataflowVariable()

        final DefaultExchange exchange = new DefaultExchange(template.getCamelContext())
        exchange.in.body = body
        exchange.in.headers = headers

        try {
            template.asyncCallback(endpoint, exchange.copy(), new SynchronizationAdapter() {
                @Override
                void onDone(Exchange doneExchange) {
                    if (doneExchange.isFailed()) {
                        result.bind(doneExchange.getException())
                    } else {
                        if (type != null) {
                            if (Exchange.class.equals(type)) {
                                result.bind(doneExchange)
                            } else {
                                result.bind(template.getCamelContext().getTypeConverter().convertTo(type, doneExchange.out.body))
                            }
                        } else {
                            result.bind(doneExchange.out.body)
                        }
                    }

                }
            })
        } catch (Exception e) {
            result.bind(e)
        }


        return result
    }

}
