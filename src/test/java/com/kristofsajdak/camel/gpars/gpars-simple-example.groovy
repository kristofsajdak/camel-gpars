package com.kristofsajdak.camel.gpars

import groovyx.gpars.dataflow.Promise

import static groovyx.gpars.dataflow.Dataflow.task
import static groovyx.gpars.dataflow.Dataflow.whenAllBound


// task outcomes are returned as promises and executed concurrently
final Promise p1 = task { "foo" }
final Promise p2 = task { "bar" }

// when both promises are bound
whenAllBound(p1, p2, { p1Bound, p2Bound ->
    println "$p1Bound $p2Bound" // this prints foobar
}).join()




