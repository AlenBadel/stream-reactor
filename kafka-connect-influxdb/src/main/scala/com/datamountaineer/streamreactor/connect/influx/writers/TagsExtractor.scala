/*
 *  Copyright 2017 Datamountaineer.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.influx.writers

import com.datamountaineer.connector.config.Tag
import com.datamountaineer.streamreactor.connect.influx.StructFieldsExtractor
import org.apache.kafka.connect.data._
import org.apache.kafka.connect.sink.SinkRecord
import org.influxdb.dto.Point
import org.json4s.jackson.JsonMethods.parse

import scala.collection.JavaConversions._
import scala.util.Try

object TagsExtractor {
  def apply(record: SinkRecord, tags: Seq[Tag], pointBuilder: Point.Builder): Point.Builder = {
    Option(record.valueSchema()) match {
      case None =>
        record.value() match {
          case map: java.util.Map[_, _] => fromMap(map.asInstanceOf[Map[String, Any]], tags, pointBuilder)
          case _ => sys.error("For schemaless record only String and Map types are supported")
        }
      case Some(schema: Schema) =>
        schema.`type`() match {
          case Schema.Type.STRING => fromJson(record.value().asInstanceOf[String], tags, pointBuilder, record)
          case Schema.Type.STRUCT => fromStruct(record.value().asInstanceOf[Struct], tags, pointBuilder)
          case other => sys.error(s"$other schema is not supported")
        }
    }
  }

  def fromJson(value: String, tags: Seq[Tag], pointBuilder: Point.Builder, record: SinkRecord): Point.Builder = {
    lazy val json = {
      Try {
        implicit val formats = org.json4s.DefaultFormats
        parse(value).extract[Map[String, Any]]
      }.getOrElse(sys.error(s"Invalid json with the record on topic ${record.topic} and offset ${record.kafkaOffset()}"))
    }
    tags.foldLeft(pointBuilder) { case (pb, t) =>
      if (t.isConstant) pb.tag(t.getKey, t.getValue)
      else {
        Option(json.getOrElse(t.getKey, throw new IllegalArgumentException(s"${t.getKey} can't be found on the values list:${json.keys.mkString(",")}")))
          .map { value =>
            pb.tag(t.getKey, value.toString)
          }.getOrElse(pb)
      }
    }
  }

  def fromMap(map: Map[String, Any], tags: Seq[Tag], pointBuilder: Point.Builder): Point.Builder = {
    tags.foldLeft(pointBuilder) { case (pb, t) =>
      if (t.isConstant) pb.tag(t.getKey, t.getValue)
      else {
        Option(map.getOrElse(t.getKey, throw new IllegalArgumentException(s"${t.getKey} can't be found on the values list:${map.keys.mkString(",")}")))
          .map { value =>
            pb.tag(t.getKey, value.toString)
          }.getOrElse(pb)
      }
    }
  }

  def fromStruct(struct: Struct, tags: Seq[Tag], pointBuilder: Point.Builder): Point.Builder = {
    tags.foldLeft(pointBuilder) { case (pb, t) =>
      if (t.isConstant) pb.tag(t.getKey, t.getValue)
      else {
        Option(struct.schema().field(t.getKey)).getOrElse(throw new IllegalArgumentException(s"${t.getKey} is not found in the list of fields:${struct.schema().fields().map(_.name()).mkString(",")}"))

        val schema = struct.schema().field(t.getKey).schema()
        val value = schema.name() match {
          case Decimal.LOGICAL_NAME => Decimal.toLogical(schema, struct.getBytes(t.getKey))
          case Date.LOGICAL_NAME => StructFieldsExtractor.DateFormat.format(Date.toLogical(schema, struct.getInt32(t.getKey)))
          case Time.LOGICAL_NAME => StructFieldsExtractor.TimeFormat.format(Time.toLogical(schema, struct.getInt32(t.getKey)))
          case Timestamp.LOGICAL_NAME => StructFieldsExtractor.DateFormat.format(Timestamp.toLogical(schema, struct.getInt64(t.getKey)))
          case _ => struct.get(t.getKey)
        }

        Option(value).map(v => pb.tag(t.getKey, v.toString)).getOrElse(pb)
      }
    }
  }
}
