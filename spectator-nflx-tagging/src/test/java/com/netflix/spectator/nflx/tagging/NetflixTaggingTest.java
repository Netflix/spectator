/*
 * Copyright 2014-2023 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.nflx.tagging;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class NetflixTaggingTest {
  private static Map<String, String> sampleEnvironmentVars() {
    Map<String, String> vars = new HashMap<>();
    vars.put("NETFLIX_ACCOUNT_ID", "1234567890");
    vars.put("EC2_AMI_ID", "ami-54321");
    vars.put("NETFLIX_APP", "foo");
    vars.put("NETFLIX_AUTO_SCALE_GROUP", "foo-bar-s1abc-s2def-v001");
    vars.put("NETFLIX_CLUSTER", "foo-bar-s1abc-s2def");
    vars.put("NETFLIX_INSTANCE_ID", "i-12345");
    vars.put("NETFLIX_PROCESS_NAME", "www");
    vars.put("NETFLIX_REGION", "us-east-1");
    vars.put("NETFLIX_SHARD1", "abc");
    vars.put("NETFLIX_SHARD2", "def");
    vars.put("NETFLIX_STACK", "bar");
    vars.put("EC2_INSTANCE_TYPE", "m5.large");
    vars.put("EC2_AVAILABILITY_ZONE", "us-east-1e");
    vars.put("TITUS_CONTAINER_NAME", "main");

    vars.put("NETFLIX_ACCOUNT_TYPE", "example");
    vars.put("NETFLIX_BUILD_JOB", "http://build/foo/42");
    vars.put("NETFLIX_BUILD_SOURCE_REPO", "http://source/foo/42");
    vars.put("NETFLIX_BUILD_BRANCH", "main");
    vars.put("NETFLIX_BUILD_COMMIT", "abcd1234");

    vars.put("MANTIS_JOB_NAME", "foo");
    vars.put("MANTIS_JOB_ID", "12");
    vars.put("MANTIS_WORKER_INDEX", "0");
    vars.put("MANTIS_WORKER_NUMBER", "4");
    vars.put("MANTIS_WORKER_STAGE_NUMBER", "5");
    vars.put("MANTIS_USER", "bob@example.com");
    return vars;
  }

  private static Map<String, String> sampleExpectedTags() {
    Map<String, String> vars = new HashMap<>();
    vars.put("nf.account", "1234567890");
    vars.put("nf.ami", "ami-54321");
    vars.put("nf.app", "foo");
    vars.put("nf.asg", "foo-bar-s1abc-s2def-v001");
    vars.put("nf.cluster", "foo-bar-s1abc-s2def");
    vars.put("nf.container", "main");
    vars.put("nf.node", "i-12345");
    vars.put("nf.process", "www");
    vars.put("nf.region", "us-east-1");
    vars.put("nf.shard1", "abc");
    vars.put("nf.shard2", "def");
    vars.put("nf.stack", "bar");
    vars.put("nf.vmtype", "m5.large");
    vars.put("nf.zone", "us-east-1e");

    vars.put("accountType", "example");
    vars.put("buildUrl", "http://build/foo/42");
    vars.put("sourceRepo", "http://source/foo/42");
    vars.put("branch", "main");
    vars.put("commit", "abcd1234");

    vars.put("mantisJobName", "foo");
    vars.put("mantisJobId", "12");
    vars.put("mantisWorkerIndex", "0");
    vars.put("mantisWorkerNumber", "4");
    vars.put("mantisWorkerStageNumber", "5");
    vars.put("mantisUser", "bob@example.com");
    return vars;
  }

  private static Map<String, String> atlasExpectedTags() {
    Map<String, String> expected = sampleExpectedTags();
    expected.remove("nf.ami");
    expected.remove("accountType");
    expected.remove("buildUrl");
    expected.remove("sourceRepo");
    expected.remove("branch");
    expected.remove("commit");
    expected.put("mantisUser", "bob_example.com");
    return expected;
  }

  @Test
  public void commonTags() {
    Map<String, String> vars = sampleEnvironmentVars();
    Map<String, String> expected = sampleExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsWhitespaceIgnored() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("NETFLIX_APP", "    foo \t\t");
    Map<String, String> expected = sampleExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsNullIgnored() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.remove("NETFLIX_SHARD2");
    Map<String, String> expected = sampleExpectedTags();
    expected.remove("nf.shard2");
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsEmptyIgnored() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("NETFLIX_SHARD2", "");
    Map<String, String> expected = sampleExpectedTags();
    expected.remove("nf.shard2");
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsForAtlas() {
    Map<String, String> vars = sampleEnvironmentVars();
    Map<String, String> expected = atlasExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTagsForAtlas(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsForAtlasSkipNode() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("ATLAS_SKIP_COMMON_TAGS", "nf.node");
    Map<String, String> expected = atlasExpectedTags();
    expected.remove("nf.node");
    Map<String, String> actual = NetflixTagging.commonTagsForAtlas(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsForAtlasSkipMultiple() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("ATLAS_SKIP_COMMON_TAGS", "nf.node, , nf.zone\n\t,nf.asg,,");
    Map<String, String> expected = atlasExpectedTags();
    expected.remove("nf.asg");
    expected.remove("nf.node");
    expected.remove("nf.zone");
    Map<String, String> actual = NetflixTagging.commonTagsForAtlas(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsForAtlasSkipEmpty() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("ATLAS_SKIP_COMMON_TAGS", "");
    Map<String, String> expected = atlasExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTagsForAtlas(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsFallbackToEc2Region() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("EC2_REGION", vars.get("NETFLIX_REGION"));
    vars.remove("NETFLIX_REGION");
    Map<String, String> expected = sampleExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsFallbackToOwnerId() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("EC2_OWNER_ID", vars.get("NETFLIX_ACCOUNT_ID"));
    vars.remove("NETFLIX_ACCOUNT_ID");
    Map<String, String> expected = sampleExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsFallbackToTitusTaskId() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("TITUS_TASK_INSTANCE_ID", vars.get("NETFLIX_INSTANCE_ID"));
    vars.remove("NETFLIX_INSTANCE_ID");
    Map<String, String> expected = sampleExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsFallbackToEc2InstanceId() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("EC2_INSTANCE_ID", vars.get("NETFLIX_INSTANCE_ID"));
    vars.remove("NETFLIX_INSTANCE_ID");
    Map<String, String> expected = sampleExpectedTags();
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void commonTagsTitusOverridesEc2InstanceId() {
    Map<String, String> vars = sampleEnvironmentVars();
    vars.put("EC2_INSTANCE_ID", vars.get("NETFLIX_INSTANCE_ID"));
    vars.put("TITUS_TASK_INSTANCE_ID", "titus-12345");
    vars.remove("NETFLIX_INSTANCE_ID");
    Map<String, String> expected = sampleExpectedTags();
    expected.put("nf.node", "titus-12345");
    Map<String, String> actual = NetflixTagging.commonTags(vars::get);
    Assertions.assertEquals(expected, actual);
  }
}
