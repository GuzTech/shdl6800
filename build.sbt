name := "shdl6800"

version := "1.0"

scalaVersion := "2.11.12"

EclipseKeys.withSource := true

libraryDependencies ++= Seq(
  "com.github.spinalhdl" % "spinalhdl-core_2.11" % "1.3.6",
  "com.github.spinalhdl" % "spinalhdl-lib_2.11" % "1.3.6"

// Use these if you want to use the dev branch of SpinalHDL.
// See: https://spinalhdl.github.io/SpinalDoc-RTD/SpinalHDL/About%20SpinalHDL/faq.html#how-to-use-an-unreleased-version-of-spinalhdl-but-commited-on-git
//
//  "com.github.spinalhdl" % "spinalhdl-core_2.11" % "1.3.7",
//  "com.github.spinalhdl" % "spinalhdl-lib_2.11" % "1.3.7"
)

fork := true
