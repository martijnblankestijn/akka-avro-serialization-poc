/*
 * Copyright 2017 Martijn Blankestijn
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

package nl.codestar.serializers

import java.io.InputStream

import com.sksamuel.avro4s.{AvroBinaryInputStream, FromRecord, SchemaFor}
import org.apache.avro.Schema

object MyAvroInputStream {
  def binary[T: SchemaFor: FromRecord](in: InputStream,
                                       writerSchema: Schema,
                                       readerSchema: Schema): AvroBinaryInputStream[T] =
    new AvroBinaryInputStream[T](in, Option(writerSchema), Option(readerSchema))
}
