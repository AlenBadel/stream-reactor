/*
 * Copyright 2017 Datamountaineer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.hbase.avro

import com.datamountaineer.streamreactor.connect.hbase.PersonAvroSchema
import org.apache.avro.Schema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AvroSchemaFieldsExistFnTest extends AnyWordSpec with Matchers {
  val schema: Schema = new Schema.Parser().parse(PersonAvroSchema.schema)

  "AvroSchemaFieldsExistFn" should {
    "raise an exception if the field is not present" in {
      intercept[IllegalArgumentException] {
        AvroSchemaFieldsExistFn(schema, Seq("notpresent"))
      }

      intercept[IllegalArgumentException] {
        AvroSchemaFieldsExistFn(schema, Seq(" lastName"))
      }
    }

    "not raise an exception if the fields are present" in {
      AvroSchemaFieldsExistFn(schema, Seq("lastName", "age", "address"))
    }
  }
}
