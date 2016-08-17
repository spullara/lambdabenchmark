# lambdabenchmark
Benchmarking AWS Lambda invocations

This benchmark ramps up requests per second and concurrency in the first case without bound. In the second case bounded at 100 concurrent requests. You can see that you get the full 1000 rps and 100 concurrent requests you are by default quota'd.

![Screenshot of benchmark graphs](https://dl.dropboxusercontent.com/s/mheethip1jjmxul/Screenshot%202016-08-17%2016.36.39.png)

[Interactive graphs](http://sampullara.com/lambda.html)
