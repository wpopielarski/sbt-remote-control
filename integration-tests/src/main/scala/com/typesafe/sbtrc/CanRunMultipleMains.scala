package com.typesafe.sbtrc

import com.typesafe.sbtrc.protocol._
import com.typesafe.sbtrc.it._
import java.io.File
import akka.actor._
import akka.pattern._
import akka.dispatch._
import concurrent.duration._
import concurrent.Await
import akka.util.Timeout

/** Ensures that we can make requests and receive responses from our children. */
class CanRunMultipleMains extends SbtProcessLauncherTest {
  val dummy = utils.makeDummySbtProjectWithMultipleMain("runSelectingAMain")
  val child = SbtProcess(system, dummy, sbtProcessLauncher)
  try {
    Await.result(child ? RunRequest(sendEvents = false, mainClass = Some("Main2")), timeout.duration) match {
      case RunResponse(success, "run-main") =>
      case whatever => throw new AssertionError("did not get RunResponse got " + whatever)
    }
    Await.result(child ? RunRequest(sendEvents = false, mainClass = Some("Main3")), timeout.duration) match {
      case RunResponse(success, "run-main") =>
      case whatever => throw new AssertionError("did not get RunResponse got " + whatever)
    }
  } finally {
    system.stop(child)
  }
}