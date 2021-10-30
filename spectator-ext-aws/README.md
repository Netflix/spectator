# spectator-ext-aws
Use Spectator as the Metrics backend for AWS SDK Request Metrics.

# Usage
The spectator-ext-aws module can be plugged into the SDK globally using:

`````java
SpectatorMetricsCollector collector = new SpectatorMetricsCollector(Spectator.globalRegistry());
AwsSdkMetrics.setMetricCollector(collector);
`````

A specific AWS SDK Client can be instrumented using:

`````java
SpectatorRequestMetricCollector requestMetricCollector = new SpectatorRequestMetricCollector(Spectator.globalRegistry());
AmazonEC2 client = new AmazonEC2Client(credentialsProvider, clientConfiguration, requestMetricCollector);
`````

Alternatively a single request can be instrumented using:
`````java
SpectatorRequestMetricCollector requestMetricCollector = new SpectatorRequestMetricCollector(Spectator.globalRegistry());
DescribeInstancesRequest awsRequest = new DescribeInstancesRequest().withRequestMetricCollector(requestMetricCollector());
`````

Any combination of the above three techniques will work, and the most specific (instance > client > SDK) `RequestMetricCollector`
is used. This could potentially allow capturing some subset of metrics into an alternative Registry.

# Metrics

Each metric is tagged with:

tag             | description
----------------|------------
serviceName     | the name of the AWS service
serviceEndpoint | the endpoint used for the request
statusCode      | the HTTP status code returned
error           | whether there was an error
requestType     | the type of request that was made (e.g. `DescribeInstancesRequest`)

Additionally, an error request is tagged with

tag             | description
----------------|------------
aWSErrorCode    | the aws specific error code
exception       | the exception type that occurred (e.g. `IOException`)

If a tag value is not available, `UNKNOWN` is used.

The following request metrics are captured as counters:

metric name                      | SDK metric
---------------------------------|-----------
aws.request.bytesProcessed       | AWSRequestMetrics.Field.BytesProcessed,
aws.request.httpClientRetryCount | AWSRequestMetrics.Field.HttpClientRetryCount,
aws.request.requestCount         | AWSRequestMetrics.Field.RequestCount

The following request metrics are captured as timers:

metric name                               | SDK metric
------------------------------------------|-----------
aws.request.clientExecuteTime             | Field.ClientExecuteTime,
aws.request.credentialsRequestTime        | Field.CredentialsRequestTime,
aws.request.httpClientReceiveResponseTime | Field.HttpClientReceiveResponseTime,
aws.request.httpClientSendRequestTime     | Field.HttpClientSendRequestTime,
aws.request.HttpRequestTime               | Field.HttpRequestTime,
aws.request.RequestMarshallTime           | Field.RequestMarshallTime,
aws.request.RequestSigningTime            | Field.RequestSigningTime,
aws.request.responseProcessingTime        | Field.ResponseProcessingTime,
aws.request.retryPauseTime                | Field.RetryPauseTime

The following request metrics are captured as gauges:

metric name                              | SDK metric
-----------------------------------------|-----------
aws.request.httpClientPoolAvailableCount | AWSRequestMetrics.Field.HttpClientPoolAvailableCount,
aws.request.httpClientPoolLeasedCount    | AWSRequestMetrics.Field.HttpClientPoolLeasedCount,
aws.request.httpClientPoolPendingCount   | AWSRequestMetrics.Field.HttpClientPoolPendingCount

Any throttling exception that occurs is tracked in a timer `aws.request.throttling` with the same
set of tags as the other metrics, and one additional tag `throttleException` containing the exception
that caused the throttling to occur.

# Tag via handler context

To help distinguish metrics from multiple clients, it's possible to specify a
[HandlerContextKey](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/handlers/HandlerContextKey.html)
for a SpectatorRequestMetricCollector (HandlerContextKey.OPERATION_NAME by
default).  The SpectatorRequestMetricCollector looks for a value from each
request's handler context for the given key, and, if there's a value for that
key, adds a tag to each metric of the form

`handlerContextKey.getName()`:value

One way to put information into the handler context that's the same for all
requests from a particular client is:

`````java
class MyRequestHandler extends RequestHandler2 {
  @Override
  public void beforeRequest(Request<?> request) {
    request.addHandlerContext(HandlerContextKey.OPERATION_NAME, "myValue");
  }
}
`````
and then, for an s3 client for example:

`````java
MyRequestHandler myRequestHandler = new MyRequestHandler();
AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
builder.setRequestHandlers(myRequestHandler);
`````

Note that a custom handler context key (e.g. new
HandlerContextKey<String>("myContextKey") works in addition to the predefined
ones in HandlerContextKey.
