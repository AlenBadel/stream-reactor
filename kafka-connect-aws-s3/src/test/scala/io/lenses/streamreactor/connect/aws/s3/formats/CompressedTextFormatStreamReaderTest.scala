/*
 * Copyright 2020 Lenses.io
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

package io.lenses.streamreactor.connect.aws.s3.formats

import io.lenses.streamreactor.connect.aws.s3.model.location.RemoteS3PathLocation
import io.lenses.streamreactor.connect.aws.s3.model.StringSourceData
import io.lenses.streamreactor.connect.aws.s3.model.StructSinkData
import io.lenses.streamreactor.connect.aws.s3.utils.TestSampleSchemaAndData
import io.lenses.streamreactor.connect.aws.s3.utils.TestSampleSchemaAndData.firstUsers
import io.lenses.streamreactor.connect.aws.s3.utils.TestSampleSchemaAndData.topic
import io.lenses.streamreactor.connect.aws.s3.stream.S3ByteArrayOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Date

class CompressedTextFormatStreamReaderTest extends AnyFlatSpec with Matchers {

  "read" should "take read through all records" in {

    val byteArrayInputStream: ByteArrayInputStream = writeRecordsToOutputStream
    val avroFormatStreamReader =
      new CompressedTextFormatStreamReader(() => byteArrayInputStream, RemoteS3PathLocation("test-bucket", "test-path"))

    avroFormatStreamReader.hasNext should be(true)
    avroFormatStreamReader.next() should be(StringSourceData(TestSampleSchemaAndData.recordsAsJson(0), 0))
    avroFormatStreamReader.hasNext should be(true)
    avroFormatStreamReader.next() should be(StringSourceData(TestSampleSchemaAndData.recordsAsJson(1), 1))
    avroFormatStreamReader.hasNext should be(true)
    avroFormatStreamReader.next() should be(StringSourceData(TestSampleSchemaAndData.recordsAsJson(2), 2))
    avroFormatStreamReader.hasNext should be(false)

  }

  private def writeRecordsToOutputStream = {
    val outputStream     = new S3ByteArrayOutputStream()
    val date = new Date()
    val jsonFormatWriter = new JsonFormatWriter(() => outputStream)
    firstUsers.foreach(data => {
      val dateString = date.toString + "\t"
      outputStream.write(dateString.getBytes)
      jsonFormatWriter.write(None, StructSinkData(data), topic)
    }
    )
    jsonFormatWriter.complete()

    val encodedResult = Base64.getEncoder.encode(outputStream.toByteArray)
    val bos = new ByteArrayOutputStream(encodedResult.length)
    val compressedResult = new ZstdCompressorOutputStream(bos)
    compressedResult.write(encodedResult)
    compressedResult.close()

    val byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray)
    byteArrayInputStream
  }
}