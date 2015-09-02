# spectator-ext-aws
Use Spectator as the Metrics backend for the AWS SDK.

# Usage
The spectator-ext-aws module is plugged into the AWS SDK using:

`````java
SpectatorMetricsCollector collector = new SpectatorMetricsCollector(Spectator.globalRegistry());
AwsSdkMetrics.setMetricCollector(collector);
`````

