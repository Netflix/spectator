# IPC

This is a description of the **Common IPC Metrics** that can be published by various IPC
libraries, with the goal of allowing consolidated monitoring and analysis across differing
IPC implementations.

## Dimensions Common to All Metrics

Not all dimensions are applicable for all of the metrics, and later in the sections 
for each specific metric, the applicable dimensions are specified.

Also note that not all dimensions have been implemented or are applicable for _all_
implementations.

* `ipc.protocol`: A short name of the network protocol in use, eg. `grpc`, `http_1`,
  `http_2`, `udp`, etc ...
* `ipc.vip`: The Eureka VIP address used to find the the server.
* `ipc.result`: Was this considered by the implementation to be successful. Allowed Values =
  [`success`, `failure`].
* `ipc.status`: One of a predefined list of status values indicating the general result, eg.
  success, bad_request, timeout, etc… See the _ipc.status values section below_.
* `ipc.status.detail`: For cases where the ipc.status needs to be further subdivided, this tag
  can hold an additional more specific detail, likely ipc-implementation specific. eg status of
  connection_error and detail of no_servers / connect_timeout / ssl_handshake_failure.
* `ipc.failure.injected`: Indicates that an artificial failure was injected into the request
  processing for testing purposes. The outcome of that failure will be reflected in the other
  error tags. Allowed Values = [true]
* `ipc.endpoint`: The name of the endpoint/function/feature the message was sent to within
  the server (eg. the URL path prefix for a java servlet, or the grpc endpoint name).
* `ipc.attempt`: Which attempt at sending this message is this. Allowed Values =
  [`initial`, `second`, `third_up`] (`initial` is the first attempt, `second` is 2nd attempt
  but first *retry*, `third_up` means third or higher attempt).
* `ipc.attempt.final`: Indicates if this request was the final attempt of potentially multiple
  retry attempts. Allowed Values = [`true`, `false`].
* `ipc.server.app`: The `nf.app` of the server the message is being sent *to*.
* `ipc.server.cluster`: The `nf.cluster` of the server the message is being sent *to*.
* `ipc.server.asg`: The `nf.asg` of the server the message is being sent *to*.
* `ipc.client.app`: The `nf.app` of the server the message is being sent *from*.
* `ipc.client.cluster`: The `nf.cluster` of the server the message is being sent *from*.
* `ipc.client.asg`: The `nf.asg` of the server the message is being sent *from*.
* `owner`: The library/impl publishing the metrics, eg. evcache, zuul, grpc, nodequark,
  platform_1_ipc, geoclient, etc ...
* `id`: Conceptual name of service. Equivalent of RestClient name in NIWS.

### Allowed Values for `ipc.status` Dimension

* `success`: The request was successfully processed and responded to, as far as the client or
  server know.
* `bad_request`: There was a problem with the clients' request causing it not to be fulfilled.
* `unexpected_error`: The client or server encountered an unexpected error processing the request.
* `connection_error`: There was an error with the underlying network connection either during
  establishment or while in use.
* `unavailable`: There were no servers available to process the request.
* `throttled`: The request was rejected due to the client or server considering the server to
  be above capacity.
* `timeout`: The request could not or would not be complete within the configured threshold
  (either on client or server).
* `cancelled`: The client cancelled the request before it was completed.
* `access_denied`: The request was denied access for authentication or authorization reasons.

## Server Metrics

### ipc.server.call

This is a [percentile timer] that is recorded for each inbound message to a server.

**Dimensions:**

* `ipc.protocol`
* `ipc.result`
* `ipc.vip`
* `ipc.endpoint`
* `ipc.status`
* `ipc.status.detail`
* `ipc.failure.injected`
* `ipc.attempt`
* `ipc.client.app`
* `ipc.client.cluster`
* `ipc.client.asg`
* `owner`
* `id`

### ipc.server.call.size.inbound

This is a [distribution summary] of the size in bytes of inbound messages received by a server.

**Dimensions:**

* `ipc.protocol`
* `ipc.vip`
* `ipc.endpoint`
* `ipc.result`
* `ipc.status`
* `ipc.status.detail`
* `ipc.client.app`
* `ipc.client.cluster`
* `ipc.client.asg`
* `owner`
* `id`

### ipc.server.call.size.outbound

This is a [distribution summary] of the size in bytes of outbound messages sent from a server.

**Dimensions:**

* `ipc.protocol`
* `ipc.vip`
* `ipc.endpoint`
* `ipc.result`
* `ipc.status`
* `ipc.status.detail`
* `ipc.client.app`
* `ipc.client.cluster`
* `ipc.client.asg`
* `owner`
* `id`

### ipc.server.inflight

This is a [distribution summary] that shows the number of inbound IPC messages currently being
processed in a server.

**Dimensions:**

* `ipc.protocol`
* `ipc.endpoint`
* `ipc.client.app`
* `ipc.client.cluster`
* `ipc.client.asg`
* `owner`
* `id`

## Client Metrics

### ipc.client.call

This is a [percentile timer] that is recorded for each outbound message from a client.

**Dimensions:**

* `ipc.protocol`
* `ipc.result`
* `ipc.vip`
* `ipc.endpoint`
* `ipc.status`
* `ipc.status.detail`
* `ipc.failure.injected`
* `ipc.attempt`
* `ipc.attempt.final`
* `ipc.server.app`
* `ipc.server.cluster`
* `ipc.server.asg`
* `owner`
* `id`

### ipc.client.call.size.inbound

This is a [distribution summary] of the size in bytes of inbound messages received by a client.

**Dimensions:**

* `ipc.protocol`
* `ipc.vip`
* `ipc.endpoint`
* `ipc.result`
* `ipc.status`
* `ipc.status.detail`
* `ipc.server.app`
* `ipc.server.cluster`
* `ipc.server.asg`
* `owner`
* `id`

### ipc.client.call.size.outbound

This is a [distribution summary] of the size in bytes of outbound messages sent from a client.

**Dimensions:**

* `ipc.protocol`
* `ipc.vip`
* `ipc.endpoint`
* `ipc.result`
* `ipc.status`
* `ipc.status.detail`
* `ipc.server.app`
* `ipc.server.cluster`
* `ipc.server.asg`
* `owner`
* `id`

### ipc.client.inflight

This is a [distribution summary] that shows the number of currently outstanding outbound
IPC messages from a client.

**Dimensions:**

* `ipc.protocol`
* `ipc.vip`
* `ipc.endpoint`
* `ipc.server.app`
* `ipc.server.cluster`
* `ipc.server.asg`
* `owner`
* `id`

[percentile timer]: https://www.javadoc.io/page/com.netflix.spectator/spectator-api/latest/com/netflix/spectator/api/histogram/PercentileTimer.html
[distribution summary]: ../intro/dist-summary.md