/**
 * Copyright 2015, deepsense.ai
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

import sbtassembly.PathList

name := "seahorse-workflowexecutor"

libraryDependencies ++= Dependencies.workflowexecutor

unmanagedClasspath in Runtime += (baseDirectory.value / "conf")

// Include PyExecutor code in assembled uber-jar (under path inside jar: /pyexecutor)
unmanagedResourceDirectories in Compile += { baseDirectory.value / "../python" }

unmanagedResourceDirectories in Compile += { baseDirectory.value / "./rexecutor" }

enablePlugins(DeepsenseBuildInfoPlugin)

buildInfoPackage := "ai.deepsense.workflowexecutor.buildinfo"

target in assembly := new File("target")
assemblyJarName in assembly := "workflowexecutor.jar"