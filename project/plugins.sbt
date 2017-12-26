logLevel := Level.Warn

// Informative Scala compiler errors
addSbtPlugin("com.softwaremill.clippy" % "plugin-sbt" % "0.5.3")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0")

// Display your SBT project's dependency updates.
// Task: dependencyUpdates
// From https://github.com/rtimush/sbt-updates
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.3")

// Format the sources
addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.14")
// Add headers to files
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "4.0.0")