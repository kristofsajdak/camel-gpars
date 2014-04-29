package com.kristofsajdak.camel.gpars

import org.apache.camel.CamelContext
import org.apache.camel.ThreadPoolRejectedPolicy
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.SimpleRegistry
import org.apache.camel.spi.ThreadPoolProfile

import java.util.concurrent.TimeUnit

class TestSupport {

    static def CamelContext camelContext(String name) {

        final registry = new SimpleRegistry()
        final context = new DefaultCamelContext(registry)
        context.setName(name)

        final manager = context.getExecutorServiceManager()

        def defaultProfile = new ThreadPoolProfile("my-custom-profile")
        defaultProfile.setDefaultProfile(true);
        defaultProfile.setPoolSize(20);
        defaultProfile.setMaxPoolSize(100);
        defaultProfile.setKeepAliveTime(60L);
        defaultProfile.setTimeUnit(TimeUnit.SECONDS);
        defaultProfile.setMaxQueueSize(2000);
        defaultProfile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);

        manager.setDefaultThreadPoolProfile(defaultProfile)

        return context
    }
}


