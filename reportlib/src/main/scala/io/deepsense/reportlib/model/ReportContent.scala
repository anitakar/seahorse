/**
 * Copyright 2015, deepsense.io
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

package io.deepsense.reportlib.model

import org.apache.spark.sql.types.StructType

import io.deepsense.reportlib.model.ReportType.ReportType

case class ReportContent(
    name: String,
    reportType: ReportType,
    tables: Map[String, Table] = Map(),
    distributions: Map[String, Distribution] = Map(),
    schema: Option[StructType] = None)

object ReportContent {

  def apply(name: String, reportType: ReportType, tables: List[Table]): ReportContent =
    ReportContent(
      name,
      reportType,
      tables.map(t => t.name -> t).toMap,
      Map())
}
