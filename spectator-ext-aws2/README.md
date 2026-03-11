## Description

Provides [common IPC](../spectator-ext-ipc/README.md) metrics for [AWS SDK for Java V2].
If this library is on the classpath, then the metrics will be collected and reported to
the global registry.

In addition to the IPC metrics, an `aws.requests` counter is recorded for each SDK call
with the following dimensions:

| Tag           | Description                                          |
|---------------|------------------------------------------------------|
| `aws.service` | AWS service name, e.g., `EC2`.                       |
| `aws.op`      | Operation name, e.g., `DescribeInstances`.           |
| `aws.region`  | AWS region the client is targeting, or `unknown`.    |
| `aws.account` | AWS account ID from the resolved identity, or `unknown`. |
| `result`      | `success`, `failure`, or `throttled`.                |

This counter is separate from the IPC metrics and provides visibility into the target
account and region, which is useful for control planes that assume roles across multiple
AWS accounts.

[AWS SDK for Java V2]: https://github.com/aws/aws-sdk-java-v2

## Gradle

```
compile "com.netflix.spectator:spectator-ext-aws2:${version}"
```