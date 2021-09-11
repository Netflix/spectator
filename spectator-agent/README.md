## Description

Java agent for collecting data and reporting to Atlas without modifying the application.

## Usage

The standalone jar can be retrieved from Maven Central.

```
https://repo1.maven.org/maven2/com/netflix/spectator/spectator-agent/${version}/spectator-agent-${version}-shadow.jar
```

Then run the java process specifying the agent and configuration file to use.

```
java -javaagent:spectator-agent-${version}-shqdow.jar=file:custom.conf
```

## Configuration

The agent is configured using the [Typesafe Config](https://lightbend.github.io/config/)
format. Most use-cases will need to specify the Atlas URI to use as well as common tags
such as app and cluster to add to the data being reported.

```
netflix.spectator.agent {
  atlas {
    step = PT1M
    uri = "http://localhost:7101/api/v1/publish"

    tags = [
      {
        key = "app"
        value = "www"
      },
      {
        key = "node"
        value = "i-12345"
      }
    ]
  }
}
```

## Gradle

```
compile "com.netflix.spectator:spectator-agent:${version}:shadow"
```