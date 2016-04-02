Quick summary:

1. Names
    * Describe the measurement being collected
    * Use camel case
    * Static
    * Succinct
2. Tags
    * Should be used for dimensional drill-down
    * Be careful about combinatorial explosion
    * Tag keys should be static
    * Use `id` to distinguish between instances
3. Use base units

## Names

### Describe the measurement

### Use camel case

The main goal here is to promote consistency which makes it easier for users. The choice of style is somewhat arbitrary, camel case was chosen because:

* Used by snmp
* Used by java
* It was the most common in use at Netflix when this guideline was added

The exception to this rule is where there is an established common case. For example with Amazon regions it is preferred to use us-east-1 rather than usEast1 as it is the more common form.

### Static

There shouldn't be any dynamic content that goes into a metric name. Metric names and associated tag keys are how users will interact with the data being produced. 

### Succinct

Long names should be avoided. 

## Tags

Historically tags have been used to play one of two roles:

* *Dimensions*: dimensions are the primary use and it allows the data to be sliced and diced so it is possible to drill down into the data.
* *Namespace*: similar to packages in Java in this mode it would be used to group related data. This type of usage is discouraged.   

As a general rule it should be possible to use the name as a pivot. This means that if
just the name is selected, then the user can drill down using other dimensions and be
able to reason about the value being shown. 

As a concrete example, suppose we have two metrics:

1. The number of threads currently in a thread pool.
2. The number of rows in a database table.

### Bad approach

```java
Id poolSize = registry.createId("size")
  .withTag("class", "ThreadPool")
  .withTag("id", "server-requests");
  
Id poolSize = registry.createId("size")
  .withTag("class", "Database")
  .withTag("table", "users");  
```

In this approach, if I select the name, `size`, it will match both the version for 
ThreadPool and Database classes. So you would get a value that is the an aggregate of the number of threads and the number of items in a database. 

### Recommended

```java
Id poolSize = registry.createId("threadpool.size")
  .withTag("id", "server-requests");
  
Id poolSize = registry.createId("db.size")
  .withTag("table", "users");  
```

This variant provides enough context so that if just the name is selected the value can
be reasoned about and is at least potentially meaningful. For example if I select
`threadpool.size` I can see the total number of threads in all pools. Then I can group by or select an `id` to drill down further.


## Use base units

Keep measurements in base units where possible. For example I would rather have all timers in seconds, disk sizes should be bytes, or network rates should be bytes/second. The reason is that for my uses this usually means the unit is obvious from the name. It also means the SI prefix shown on the graph images make more sense, e.g. 1k is 1 kilobyte not 1 kilo-megabyte.