package com.kristofsajdak.camel.gpars

import groovyx.gpars.dataflow.DataflowVariable

import static groovyx.gpars.dataflow.Dataflow.whenAllBound

def exec = { Closure code ->

final d1 = new DataflowVariable()
final d2 = new DataflowVariable()
final d3 = new DataflowVariable()
final d4 = new DataflowVariable()
final d5 = new DataflowVariable()

    final bound = whenAllBound(d1, d2, { d1res, d2res ->
        d5 >> {
            whenAllBound(d3, d4, { d3res, d4res ->
                "$d1res $d2res $d3res $d4res".toString()
            })
        }
    })
    code.call (bound, d1, d2, d3, d4, d5)

}

exec {bound, d1, d2, d3, d4, d5 ->

    d1.bind("hello")
    d2.bind("world")
    d3.bind("foo")
    d4.bind("bar")
    d5.bind("")

    final result = bound.then(
        { it },
        { Exception e ->
            println "in the error handler"
            e.printStackTrace()
        }
    ).get()
    assert "hello world foo bar".equals(result)
}

exec {bound, d1, d2, d3, d4, d5 ->

    d1.bind("hello")
    d2.bind("world")
    d3.bind("foo")
    d4.bind("bar")
    d5.bind(new RuntimeException("foo ex"))

    try {
        bound.get()
    } catch (Exception e) {
        return
    }
    throw new IllegalStateException()
}

exec {bound, d1, d2, d3, d4, d5 ->

    d1.bind("hello")
    d2.bind("world")
    d3.bind(new RuntimeException())
    d4.bind("bar")
    d5.bind("")

    try {
        bound.get()
    } catch (Exception e) {
        return
    }
    throw new IllegalStateException()
}






