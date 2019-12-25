name := "shdl6800"

version := "1.0"

scalaVersion := "2.11.12"

EclipseKeys.withSource := true

libraryDependencies ++= Seq(
  "com.github.spinalhdl" % "spinalhdl-core_2.11" % "1.3.5",
  "com.github.spinalhdl" % "spinalhdl-lib_2.11" % "1.3.5"
)

fork := true
