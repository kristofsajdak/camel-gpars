camel-gpars
===========

This groovy extension module integrates Gpars Dataflows into Apache Camel.

Some example snippets can be found below, check the tests dir for full code examples with comments.

* parallel outbound calls

    ```groovy

    from("jetty:http://0.0.0.0:8080/quote").task({ Exchange exchange ->

        final headers = [(Exchange.HTTP_QUERY): exchange.in.headers[Exchange.HTTP_QUERY]]
        whenAllBound(
            template.requestBodyAndHeadersAsPromise("direct:discounts", "", headers),
            template.requestBodyAndHeadersAsPromise("direct:prices", "", headers),
            { Map discountResolved, Map priceResolved ->
                priceResolved.price * (1 - (discountResolved.discount / 100))
            }
        )
    })

    from("direct:discounts").to("ahc:http://0.0.0.0:8087/discounts").unmarshal(json)
    from("direct:prices").to("ahc:http://0.0.0.0:8088/prices").unmarshal(json)

    ```


* parallel scatter-gather with timeout

    ```groovy

    from("direct:lowest").task({ Exchange exchange ->

        final promises = [
            template.requestBodyAsPromise("direct:quote1", ""),
            template.requestBodyAsPromise("direct:quote2", ""),
            template.requestBodyAsPromise("direct:quote3", "")]

        final bound = whenAllBound(promises, { List quotes -> /* noop */ })

        select(bound, Select.createTimeout(timeout)).selectToPromise().then {
            final boundValues = promises.findResults { p -> p.isBound() ? p.get() : null }
            boundValues.inject(null) { acc, val -> (acc == null || val.price < acc) ? val.price : acc }
        }

    })

    from("direct:quote1").to("ahc:http://0.0.0.0:8080/quote").unmarshal(json)
    from("direct:quote2").to("ahc:http://0.0.0.0:8081/quote").unmarshal(json)
    from("direct:quote3").to("ahc:http://0.0.0.0:8082/quote").unmarshal(json)

    ```