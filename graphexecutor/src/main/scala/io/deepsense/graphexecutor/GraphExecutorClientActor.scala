/**
 * Copyright (c) 2015, CodiLime Inc.
 */

package io.deepsense.graphexecutor

import scala.concurrent.Future
import scala.util.Success

import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.yarn.api.records._
import org.apache.hadoop.yarn.client.api.YarnClient

import io.deepsense.commons.akka.RemoteAddressExtension
import io.deepsense.commons.exception.FailureCode._
import io.deepsense.commons.exception.{DeepSenseFailure, FailureDescription}
import io.deepsense.graphexecutor.GraphExecutorClientActor.ExecutorSpawned
import io.deepsense.graphexecutor.clusterspawner.ClusterSpawner
import io.deepsense.models.experiments.Experiment
import io.deepsense.models.messages._


class GraphExecutorClientActor(
    entityStorageLabel: String,
    spawner: ClusterSpawner)
  extends Actor with LazyLogging {

  val remoteAddress = RemoteAddressExtension(context.system).address
  val actorPath = self.path.toStringWithAddress(remoteAddress)

  var requestedExperiment: Experiment = _
  var applicationId: ApplicationId = _
  var yarnClient: YarnClient = _
  var graphExecutor: ActorRef = _
  var runningExperiments: ActorRef = _

  override def receive: Receive = waitingForExperiment orElse handleUnknownRequests

  /**
   * Represents state where the actor was just created and is waiting
   * for a launch request. All other messages are not handled.
   */
  def waitingForExperiment: Receive = {
    case Launch(experiment) => launch(experiment)
  }

  /**
   * Starts procedure of an experiment launch. Requests GE spawn on cluster.
   * Afterwards the actor waits for the spawn request to finish.
   * @param experiment An experiment to be launched.
   */
  def launch(experiment: Experiment): Unit = {
    runningExperiments = sender()
    logger.info(">>> Launch(id: {} / status: {})", experiment.id, experiment.state.status)
    requestedExperiment = experiment
    requestSpawn()
    context.become(waitingForExecutorSpawn orElse handleUnknownRequests)
  }

  /**
   * Represents the state where the spawn on cluster was requested but not yet finished.
   * Additionally, at this point of time it is possible to abort the launched experiment.
   * When an Abort request comes here then Update with an aborted experiment is returned and
   * the actor waits for the spawn to finish in order to gracefully close YarnClient.
   */
  def waitingForExecutorSpawn: Receive = {
    case ExecutorSpawned(appId, yc) => executorSpawned(appId, yc)
    case Abort(experimentId) =>
      runningExperiments ! Update(requestedExperiment.markAborted)
      context.become(spawnRequestedButAborted orElse handleUnknownRequests)
  }

  /**
   * Create a log that the executor is spawned and the spawn process is finished.
   * The actor will now wait for the executor to send a confirmation that an experiment to launch
   * should be send.
   * @param applicationId An id of the application on YARN where the experiment will be executed.
   * @param yarnClient YarnClient to manipulate the launched application.
   */
  def executorSpawned(applicationId: ApplicationId, yarnClient: YarnClient): Unit = {
    this.applicationId = applicationId
    this.yarnClient = yarnClient
    logger.info(s"ExecutorSpawned: AppId: $applicationId, YarnClient: $yarnClient")
    context.become(waitingForExecutorReady orElse handleUnknownRequests)
  }

  /**
   * Represents the state where an experiment was aborted before the spawn finished.
   * When the spawn is finished Yarn is immediately closed and the actor kills himself.
   */
  def spawnRequestedButAborted: Receive = {
    case ExecutorSpawned(appId, yc) =>
      yc.close()
      self ! PoisonPill
  }

  /**
   * Represents the state where the executor was spawned but not yet confirmed
   * that it is up and running. When the executor is up and running then it sends ExecutorReady.
   * The actor waits for the executor to be ready, sends Launch request
   * and then awaits Update()s.
   */
  def waitingForExecutorReady: Receive = {
    case Abort(experiment) => forwardAbort()
    case ExecutorReady(experimentId) =>
      logger.debug(s"Executor for $experimentId ready!")
      graphExecutor = sender()
      graphExecutor ! Launch(requestedExperiment)
      context.become(watchingExperiment orElse handleUnknownRequests)
  }

  /**
   * Represents the state when the experiment is already running and GE sends Update messages.
   * Abort and Updated messages are forwarded to the appropriate recipients. On Update
   * where the experiment is either completed or failed the actor stops the yarn client
   * and kills himself.
   */
  def watchingExperiment: Receive = {
    case Abort(experiment) => forwardAbort()
    case update @ Update(experiment) =>
      logger.debug(s"Got status update $experiment")
      runningExperiments ! update
      if (experiment.isCompleted || experiment.isFailed) {
        logger.info("Experiment [{}] / status: {} - cleaning up",
          experiment.id, experiment.state.status)
        yarnClient.stop()
        self ! PoisonPill
      }
  }

  /**
   * Asynchronously requests a spawn of GE. On success ExecutorSpawned will send to self.
   * Otherwise Update will be send to REA and the actor will kill himself.
   */
  def requestSpawn(): Unit = {
    val me = self
    import scala.concurrent.ExecutionContext.Implicits.global
    val spawnRequest =
      Future(spawner.spawnOnCluster(requestedExperiment.id, actorPath, entityStorageLabel))

    spawnRequest.onComplete {
      case Success(Success((yc, appId))) =>
        logger.info(s"Application spawned: $appId")
        me ! ExecutorSpawned(appId, yc)
      case failure =>
        val error = s"Spawning experiment on cluster failed: $failure"
        logger.error(error)
        val experimentFailureDetails = FailureDescription(
          DeepSenseFailure.Id.randomId,
          LaunchingFailure,
          error)
        runningExperiments ! Update(requestedExperiment.markFailed(experimentFailureDetails))
        self ! PoisonPill
    }
  }

  def forwardAbort(): Unit = {
    logger.debug("Aborting running experiment")
    graphExecutor ! Abort(requestedExperiment.id)
  }

  def handleUnknownRequests: Receive = {
    case message => logger.warn(s"UNHANDLED: $message from ${sender()}")
  }
}

object GraphExecutorClientActor extends LazyLogging {
  sealed trait Message
  case class ExecutorSpawned(aid: ApplicationId, yarnClient: YarnClient) extends Message
}
