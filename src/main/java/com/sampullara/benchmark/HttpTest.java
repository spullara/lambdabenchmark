package com.sampullara.benchmark;

import com.codahale.metrics.*;
import com.google.common.util.concurrent.RateLimiter;
import com.wavefront.integrations.metrics.WavefrontReporter;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpTest {

  public static final int PERMITS = 5;

  public static void main(String[] args) {
    MetricRegistry metrics = new MetricRegistry();
    WavefrontReporter reporter = WavefrontReporter.forRegistry(metrics)
            .withSource("lambdabenchmark.sampullara.com")
            .withJvmMetrics()
            .build("localhost", 2878);
    reporter.start(10, TimeUnit.SECONDS);

    /*
    The idea is to slowly ratchet up concurrency and rate and measure latency.
     */

    long startingRate = 10;
    final double multiplier = 1.2;
    metrics.counter("lambda.starting_rate").inc(startingRate);
    metrics.register("lambda.multiplier", new Gauge<Double>() {
      public Double getValue() {
        return multiplier;
      }
    });

    CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
            .setMaxConnPerRoute(PERMITS)
            .setMaxConnTotal(PERMITS)
            .setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE)
            .build();
    client.start();

    Timer invokeLatency = metrics.timer("lambda.invoke_latency");
    Timer waitTime = metrics.timer("lambda.wait_time");
    Counter invokes = metrics.counter("lambda.invokes");
    final Counter successes = metrics.counter("lambda.successes");
    final Counter errors = metrics.counter("lambda.errors");
    final AtomicInteger concurrency = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(PERMITS);
    Histogram concurrencyHistogram = metrics.histogram("lambda.concurrency");
    long start = System.currentTimeMillis();
    RateLimiter rateLimiter = RateLimiter.create(startingRate);
    long epoch = 0;
    while (true) {
      long minutes = (System.currentTimeMillis() - start) / 60000;
      if (minutes != epoch) {
        epoch = minutes;
        double permitsPerSecond = startingRate * Math.pow(multiplier, epoch);
        rateLimiter = RateLimiter.create(permitsPerSecond);
        System.out.println("PPS: " + permitsPerSecond);
      }
      Timer.Context waitTimeCtx = waitTime.time();
      rateLimiter.acquire();
      semaphore.acquireUninterruptibly();
      concurrencyHistogram.update(PERMITS - semaphore.availablePermits());
      waitTimeCtx.stop();
      HttpGet httpGet = new HttpGet("http://104.198.105.2/requests/helloriff");
      invokes.inc();
      final Timer.Context invokeLatencyCtx = invokeLatency.time();
      client.execute(httpGet, new FutureCallback<HttpResponse>() {
        @Override
        public void completed(HttpResponse httpResponse) {
          invokeLatencyCtx.stop();
          successes.inc();
          System.out.println("SUCCESS: " + successes.getCount());
          concurrency.decrementAndGet();
          semaphore.release();
        }

        @Override
        public void failed(Exception e) {
          e.printStackTrace();
          invokeLatencyCtx.stop();
          errors.inc();
          System.out.println("ERROR");
          concurrency.decrementAndGet();
          semaphore.release();
        }

        @Override
        public void cancelled() {
          failed(new CancellationException());
        }
      });
    }

  }
}
