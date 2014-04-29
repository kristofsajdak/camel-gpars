package com.kristofsajdak.camel.gpars

import groovyx.gpars.dataflow.Dataflow
import groovyx.gpars.dataflow.DataflowVariable
import org.apache.camel.AsyncCallback
import org.apache.camel.AsyncProcessor
import org.apache.camel.Exchange
import org.apache.camel.groovy.extend.ClosureSupport
import org.apache.camel.model.ProcessorDefinition
import org.apache.camel.util.AsyncProcessorHelper

class CamelDslGparsTaskExtension {


    public static ProcessorDefinition<?> task(ProcessorDefinition<?> self,
                                              Closure<?> processorLogic) {
        final asyncProcessor = new AsyncProcessor() {

            @Override
            boolean process(Exchange exchange, AsyncCallback callback) {

                final taskResult = new DataflowVariable()
                Dataflow.task {
                    try {
                        final call = ClosureSupport.call(processorLogic, exchange)
                        taskResult << call
                    } catch (Exception e) {
                        taskResult << e
                    }
                }
                taskResult.then(
                    {
                        exchange.out.body = it
                        callback.done(false)
                    },
                    { e ->
                        exchange.exception = e
                        callback.done(false)
                    })
                return false

            }


            @Override
            void process(Exchange exchange) throws Exception {
                AsyncProcessorHelper.process(this, exchange);
            }
        }
        return self.process(asyncProcessor)
    }

}

