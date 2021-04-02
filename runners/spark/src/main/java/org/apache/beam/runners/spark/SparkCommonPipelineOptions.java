/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.spark;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.beam.runners.core.construction.resources.PipelineResources;
import org.apache.beam.sdk.annotations.Internal;
import org.apache.beam.sdk.options.ApplicationNameOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.DefaultValueFactory;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.StreamingOptions;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.MoreObjects;

/**
 * Spark runner {@link PipelineOptions} handles Spark execution-related configurations, such as the
 * master address, and other user-related knobs.
 */
public interface SparkCommonPipelineOptions
    extends PipelineOptions, StreamingOptions, ApplicationNameOptions {
  String DEFAULT_MASTER_URL = "local[4]";

  @Description("The url of the spark master to connect to, (e.g. spark://host:port, local[4]).")
  @Default.String(DEFAULT_MASTER_URL)
  String getSparkMaster();

  void setSparkMaster(String master);

  @Description(
      "A checkpoint directory for streaming resilience, ignored in batch. "
          + "For durability, a reliable filesystem such as HDFS/S3/GS is necessary.")
  @Default.InstanceFactory(TmpCheckpointDirFactory.class)
  String getCheckpointDir();

  void setCheckpointDir(String checkpointDir);

  /**
   * List of local files to make available to workers.
   *
   * <p>Jars are placed on the worker's classpath.
   *
   * <p>The default value is the list of jars from the main program's classpath.
   */
  @Description(
      "Jar-Files to send to all workers and put on the classpath. "
          + "The default value is all files from the classpath.")
  List<String> getFilesToStage();

  void setFilesToStage(List<String> value);

  @Description("Enable/disable sending aggregator values to Spark's metric sinks")
  @Default.Boolean(true)
  Boolean getEnableSparkMetricSinks();

  void setEnableSparkMetricSinks(Boolean enableSparkMetricSinks);

  /**
   * Returns the default checkpoint directory of /tmp/${job.name}. For testing purposes only.
   * Production applications should use a reliable filesystem such as HDFS/S3/GS.
   */
  class TmpCheckpointDirFactory implements DefaultValueFactory<String> {
    @Override
    public String create(PipelineOptions options) {
      return "/tmp/" + options.getJobName();
    }
  }

  /** Detects if the pipeline is run in spark local mode. */
  @Internal
  static boolean isLocalSparkMaster(SparkCommonPipelineOptions options) {
    return options.getSparkMaster().matches("local\\[?\\d*]?");
  }

  /**
   * Classpath contains non jar files (eg. directories with .class files or empty directories) will
   * cause exception in running log. Though the {@link org.apache.spark.SparkContext} can handle
   * this when running in local master, it's better not to include non-jars files in classpath.
   */
  @Internal
  static void prepareFilesToStage(SparkCommonPipelineOptions options) {
    if (!isLocalSparkMaster(options)) {
      List<String> filesToStage =
          options.getFilesToStage().stream()
              .map(File::new)
              .filter(File::exists)
              .map(
                  file -> {
                    return file.getAbsolutePath();
                  })
              .collect(Collectors.toList());
      options.setFilesToStage(
          PipelineResources.prepareFilesForStaging(
              filesToStage,
              MoreObjects.firstNonNull(
                  options.getTempLocation(), System.getProperty("java.io.tmpdir"))));
    }
  }
}
