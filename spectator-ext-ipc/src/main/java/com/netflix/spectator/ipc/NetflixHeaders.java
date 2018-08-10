package com.netflix.spectator.ipc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Helper for extracting Netflix header values from the environment variables available
 * on the base ami. For more information see:
 *
 * https://github.com/Netflix/iep/tree/master/iep-nflxenv
 */
public final class NetflixHeaders {

  private static final String[] NETFLIX_ASG = {
      "NETFLIX_AUTO_SCALE_GROUP",
      "CLOUD_AUTO_SCALE_GROUP"
  };

  private static final String[] NETFLIX_NODE = {
      "TITUS_TASK_ID",
      "EC2_INSTANCE_ID"
  };

  private static final String[] NETFLIX_ZONE = {
      "EC2_AVAILABILITY_ZONE"
  };

  private static void addHeader(
      Map<String, String> headers,
      Function<String, String> env,
      NetflixHeader header,
      String[] names) {
    for (String name : names) {
      String value = env.apply(name);
      if (value != null && !value.isEmpty()) {
        headers.put(header.headerName(), value);
        break;
      }
    }
  }

  public static Map<String, String> extractFrom(Function<String, String> env) {
    Map<String, String> headers = new LinkedHashMap<>();
    addHeader(headers, env, NetflixHeader.ASG, NETFLIX_ASG);
    addHeader(headers, env, NetflixHeader.Node, NETFLIX_NODE);
    addHeader(headers, env, NetflixHeader.Zone, NETFLIX_ZONE);
    return headers;
  }

  public static Map<String, String> extractFromEnvironment() {
    return extractFrom(System::getenv);
  }
}
