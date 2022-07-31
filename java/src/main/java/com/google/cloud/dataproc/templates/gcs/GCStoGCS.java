/*
 * Copyright (C) 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.dataproc.templates.gcs;

import static com.google.cloud.dataproc.templates.util.TemplateConstants.*;

import com.google.cloud.dataproc.templates.BaseTemplate;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.spark.sql.DataFrameWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GCStoGCS implements BaseTemplate {

  public static final Logger LOGGER = LoggerFactory.getLogger(GCStoGCS.class);

  private String projectID;
  private String inputFileLocation;
  private String inputFileFormat;
  private String gcsOutputLocation;
  private String gcsWriteMode;
  private String gcsPartitionColumn;
  private String gcsOutputFormat;

  private String tempTable;

  private String tempQuery;

  public GCStoGCS() {

    projectID = getProperties().getProperty(PROJECT_ID_PROP);
    inputFileLocation = getProperties().getProperty(GCS_GCS_INPUT_LOCATION);
    inputFileFormat = getProperties().getProperty(GCS_GCS_INPUT_FORMAT);
    gcsOutputLocation = getProperties().getProperty(GCS_GCS_OUTPUT_LOCATION);
    gcsPartitionColumn = getProperties().getProperty(GCS_GCS_OUTPUT_PARTITION_COLUMN);
    gcsOutputFormat = getProperties().getProperty(GCS_GCS_OUTPUT_FORMAT);
    gcsWriteMode = getProperties().getProperty(GCS_GCS_WRITE_MODE);
    tempTable = getProperties().getProperty(GCS_GCS_TEMP_TABLE);
    tempQuery = getProperties().getProperty(GCS_GCS_TEMP_QUERY);
  }

  @Override
  public void runTemplate() {
    validateInput();

    SparkSession spark = null;
    LOGGER.info(
        "Starting GCS to GCS spark job with following parameters:"
            + "1. {}:{}"
            + "2. {}:{}"
            + "3. {}:{}"
            + "4. {}:{}"
            + "5. {}:{}",
        GCS_GCS_INPUT_LOCATION,
        inputFileLocation,
        GCS_GCS_INPUT_FORMAT,
        inputFileFormat,
        GCS_GCS_OUTPUT_LOCATION,
        gcsOutputLocation,
        GCS_GCS_OUTPUT_FORMAT,
        gcsOutputFormat,
        GCS_GCS_WRITE_MODE,
        gcsWriteMode);


      spark = SparkSession.builder().appName("GCS to GCS load").getOrCreate();

      Dataset<Row> inputData = null;

      switch (inputFileFormat) {
        case GCS_GCS_CSV_FORMAT:
          inputData =
              spark
                  .read()
                  .format(GCS_GCS_CSV_FORMAT)
                  .option(GCS_GCS_CSV_HEADER, true)
                  .option(GCS_GCS_CSV_INFOR_SCHEMA, true)
                  .load(inputFileLocation);
          break;
        case GCS_GCS_AVRO_FORMAT:
          inputData = spark.read().format(GCS_GCS_AVRO_EXTD_FORMAT).load(inputFileLocation);
          break;
        case GCS_GCS_PRQT_FORMAT:
          inputData = spark.read().parquet(inputFileLocation);
          break;
        default:
          throw new IllegalArgumentException(
              "Currently avro, parquet and csv are the only supported formats");
      }

      if (tempTable != null && tempQuery != null) {
        inputData.createOrReplaceGlobalTempView(tempTable);
        inputData = spark.sql(tempQuery);
      }

      inputData.show();

      DataFrameWriter<Row> writer = inputData.write().mode(gcsWriteMode).format(gcsOutputFormat);

      /*
       * If optional partition column is passed than partition data by partition
       * column before writing to GCS.
       * */
      if (StringUtils.isNotBlank(gcsPartitionColumn)) {
        LOGGER.info("Partitioning data by :{} cols", gcsPartitionColumn);
        writer = writer.partitionBy(gcsPartitionColumn);
      }

      spark.conf().set("mapreduce.fileoutputcommitter.marksuccessfuljobs", "false");
      writer.save(gcsOutputLocation);

      spark.stop();
  }

  void validateInput() {
    if (StringUtils.isAllBlank(projectID)
        || StringUtils.isAllBlank(inputFileLocation)
        || StringUtils.isAllBlank(inputFileFormat)
        || StringUtils.isAllBlank(gcsOutputLocation)
        || StringUtils.isAllBlank(gcsOutputFormat)
        || StringUtils.isAllBlank(gcsWriteMode)) {
      LOGGER.error(
          "{},{},{},{},{},{} are required parameter. ",
          PROJECT_ID_PROP,
          GCS_GCS_INPUT_LOCATION,
          GCS_GCS_INPUT_FORMAT,
          GCS_GCS_OUTPUT_LOCATION,
          GCS_GCS_OUTPUT_FORMAT,
          GCS_GCS_WRITE_MODE);
      throw new IllegalArgumentException(
          "Required parameters for GCStoGCS not passed. "
              + "Set mandatory parameter for GCStoGCS template "
              + "in resources/conf/template.properties file.");
    }

    LOGGER.info(
        "Starting GCS to GCS spark job with following parameters:"
            + "1. {}:{}"
            + "2. {}:{}"
            + "3. {}:{}"
            + "4. {}:{}"
            + "5. {}:{}",
        GCS_GCS_INPUT_LOCATION,
        inputFileLocation,
        GCS_GCS_INPUT_FORMAT,
        inputFileFormat,
        GCS_GCS_OUTPUT_LOCATION,
        gcsOutputLocation,
        GCS_GCS_OUTPUT_FORMAT,
        gcsOutputFormat,
        GCS_GCS_WRITE_MODE,
        gcsWriteMode);
  }
}
