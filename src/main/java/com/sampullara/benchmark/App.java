package com.sampullara.benchmark;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.LogType;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.RateLimiter;
import com.wavefront.integrations.metrics.WavefrontReporter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class App {
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

    long startingRate = 100;
    final double multiplier = 1.2;
    metrics.counter("lambda.starting_rate").inc(startingRate);
    metrics.register("lambda.multiplier", new Gauge<Double>() {
      public Double getValue() {
        return multiplier;
      }
    });

    AWSLambdaAsyncClient client = new AWSLambdaAsyncClient();
    client.setRegion(Region.getRegion(Regions.US_WEST_2));

    Timer invokeLatency = metrics.timer("lambda.invoke_latency");
    Timer waitTime = metrics.timer("lambda.wait_time");
    Counter invokes = metrics.counter("lambda.invokes");
    final Counter successes = metrics.counter("lambda.successes");
    final Counter errors = metrics.counter("lambda.errors");
    final AtomicInteger concurrency = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(100);
    Histogram concurrencyHistogram = metrics.histogram("lambda.concurrency");
    long start = System.currentTimeMillis();
    RateLimiter rateLimiter = RateLimiter.create(startingRate);
    long epoch = 0;
    while (true) {
      long minutes = (System.currentTimeMillis() - start) / 60000;
      if (minutes != epoch) {
        epoch = minutes;
        rateLimiter = RateLimiter.create(startingRate * Math.pow(multiplier, epoch));
      }
      Timer.Context waitTimeCtx = waitTime.time();
      rateLimiter.acquire();
      semaphore.acquireUninterruptibly();
      concurrencyHistogram.update(100 - semaphore.availablePermits());
      waitTimeCtx.stop();
      InvokeRequest request = new InvokeRequest()
              .withFunctionName("arn:aws:lambda:us-west-2:178871584816:function:dev-lambdas-r-LambdabenchBenchmark-RH0OPE8RLAEM:benchmark")
              .withLogType(LogType.Tail)
              .withInvocationType(InvocationType.RequestResponse);
      invokes.inc();
      final Timer.Context invokeLatencyCtx = invokeLatency.time();
      client.invokeAsync(request, new AsyncHandler<InvokeRequest, InvokeResult>() {
        public void onError(Exception e) {
          e.printStackTrace();
          invokeLatencyCtx.stop();
          errors.inc();
          System.out.println("ERROR");
          concurrency.decrementAndGet();
          semaphore.release();
        }

        public void onSuccess(InvokeRequest request, InvokeResult invokeResult) {
          invokeLatencyCtx.stop();
          successes.inc();
          System.out.println("SUCCESS: " + successes.getCount());
          concurrency.decrementAndGet();
          semaphore.release();
        }
      });
    }

  }
}
