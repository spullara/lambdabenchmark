# lambdabenchmark
Benchmarking AWS Lambda invocations

This benchmark ramps up requests per second and concurrency in the first case without bound. In the second case bounded at 100 concurrent requests. You can see that you get the full 1000 rps and 100 concurrent requests you are by default quota'd.

![Screenshot of benchmark graphs](https://photos-3.dropbox.com/t/2/AAD_bvS0aoW3641CLFM9P42BylwYmaOOz6ma7Wq02t-rUA/12/3924269/png/32x32/3/1471395600/0/2/Screenshot%202016-08-16%2013.12.25.png/EOTS-QIYm5UJIAIoAg/bj-JBGsvWOHifgbx3VKHaEF56RXRgCbnM71YFWK9Vqw?size_mode=3&size=2048x1536&dl=0)

[Interactive graphs](http://sampullara.com/lambda.html)
