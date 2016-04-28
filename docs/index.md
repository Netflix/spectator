Simple library for instrumenting code to record dimensional time series. If
you are new to the library it is highly recommended to read the pages in the
*Getting Started* section on the sidebar.

At a minimum you will need to:

1. Depend on the api library. It is in maven central, for gradle the dependency
   would be `com.netflix.spectator:spectator-api:0.38.0`.
2. Instrument some code, see the usage guides for [counters](intro/counter.md),
   [timers](intro/timer.md), and [gauges](intro/gauge.md).
3. Pick a registry to bind to when initializing the application. See the sidebar
   for a list of available registries.
