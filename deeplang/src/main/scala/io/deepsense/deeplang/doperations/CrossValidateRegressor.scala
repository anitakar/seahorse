/**
 * Copyright (c) 2015, CodiLime, Inc.
 */
package io.deepsense.deeplang.doperations

import scala.collection.mutable

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.mllib.evaluation.RegressionMetrics

import io.deepsense.deeplang._
import io.deepsense.deeplang.doperables._
import io.deepsense.deeplang.doperables.dataframe.DataFrame
import io.deepsense.deeplang.parameters._
import io.deepsense.reportlib.model.{ReportContent, Table}

class CrossValidateRegressor
  extends DOperation2To2[Trainable, DataFrame, Scorable, Report]
  with Trainer
  with LazyLogging {

  override val parameters = trainerParameters ++ ParametersSchema(
    CrossValidateRegressor.numOfFoldsParamKey ->
      NumericParameter(
        "Number of folds",
        Some(10.0),
        required = true,
        validator = RangeValidator(0, Int.MaxValue / 2, true, true, Some(1.0))),
    CrossValidateRegressor.shuffleParamKey ->
      ChoiceParameter(
        "Perform shuffle",
        Some(CrossValidateRegressor.shuffleYes),
        required = true,
        Map(
          CrossValidateRegressor.shuffleNo -> ParametersSchema(),
          CrossValidateRegressor.shuffleYes ->
            ParametersSchema(CrossValidateRegressor.seedParamKey ->
              NumericParameter("Seed value",
                default = Some(0.0),
                required = true,
                RangeValidator(Int.MinValue / 2, Int.MaxValue / 2, true, true, Some(1.0)))))))

  override val id: DOperation.Id = "95ca5225-b8e0-45c7-8ecd-a2c9d4d6861f"

  override val name: String = "CrossValidateRegressor"

  override protected def _execute(context: ExecutionContext)
                                 (trainable: Trainable,
                                  dataFrame: DataFrame): (Scorable, Report) = {
    logger.info("Execution of CrossValidateRegressor starts")
    import CrossValidateRegressor._
    // If number of folds is too big, we use dataFrame size as folds number
    val effectiveNumberOfFolds = math.min(
      // If dataFrame is of size 1, no folds can be performed
      if (dataFrame.sparkDataFrame.count() == 1) 0 else dataFrame.sparkDataFrame.count(),
      math.round(parameters.getDouble(numOfFoldsParamKey).get).toInt).toInt
    val performShuffle = parameters.getChoice(shuffleParamKey).get.label == shuffleYes
    val seed =
      if (performShuffle) {
        math.round(
          parameters.getChoice(shuffleParamKey).get.selectedSchema.getDouble(seedParamKey).get)
      } else {
        0
      }

    logger.debug("Effective effectiveNumberOfFolds=" + effectiveNumberOfFolds +
      ", DataFrame size=" + dataFrame.sparkDataFrame.count())

    // Perform shuffling if necessary
    // TODO: (DS-546) Do not use sample method, perform shuffling "in flight"
    // during splitting DataFrame on training and test DataFrame.
    val shuffledDataFrame =
      if (performShuffle) {
        context.dataFrameBuilder.buildDataFrame(dataFrame.sparkDataFrame.sample(false, 1.0, seed))
      } else {
        dataFrame
      }

    // Create initially empty report
    // Number of folds == 0 would mean that cross-validation report is not needed
    val report =
      if (effectiveNumberOfFolds > 0) {
        generateCrossValidationReport(context, trainable, shuffledDataFrame, effectiveNumberOfFolds)
      } else {
        new Report(ReportContent(this.name))
      }

    logger.info("Train regressor on all data available")
    val scorable = trainable.train(context)(parametersForTrainable)(dataFrame)

    logger.info("Execution of CrossValidateRegressor ends")
    (scorable, report)
  }

  def generateCrossValidationReport(
      context: ExecutionContext,
      trainable: Trainable,
      dataFrame: DataFrame,
      effectiveNumberOfFolds: Int): Report = {
    logger.info("Generating cross-validation report")
    val schema = dataFrame.sparkDataFrame.schema

    val rddWithIndex = dataFrame.sparkDataFrame.rdd.zipWithIndex().cache()
    val foldMetrics = new mutable.MutableList[RegressionMetricsRow]
    (0 to effectiveNumberOfFolds - 1).foreach {
      case splitIndex =>
        val k = effectiveNumberOfFolds
        logger.info("Preparing cross-validation report: split index [0..N-1]=" + splitIndex)
        val training =
          rddWithIndex.filter { case (r, index) => index % k != splitIndex }
            .map { case (r, index) => r }
        val test =
          rddWithIndex.filter { case (r, index) => index % k == splitIndex }
            .map { case (r, index) => r }

        val trainingSparkDataFrame = context.sqlContext.createDataFrame(training, schema).cache()
        val trainingDataFrame = context.dataFrameBuilder.buildDataFrame(trainingSparkDataFrame)
        val testSparkDataFrame = context.sqlContext.createDataFrame(test, schema).cache()
        val testDataFrame = context.dataFrameBuilder.buildDataFrame(testSparkDataFrame)

        // Train model on trainingDataFrame
        val trained = trainable.train(context)(parametersForTrainable)(trainingDataFrame)

        // NOTE: trainingSparkDataFrame/trainingDataFrame will not be used further
        trainingSparkDataFrame.unpersist()

        // Score model on trainingDataFrame
        val scored = trained.score(context)(None)(testDataFrame)

        // Prepare prediction and observations RDDs to facilitate computation of metrics
        val predictions =
          scored.sparkDataFrame.rdd.map(r => { r.get(r.size - 1).asInstanceOf[Double] })
        val observations =
          testDataFrame
            .sparkDataFrame
            .select(testDataFrame.getColumnName(parametersForTrainable.targetColumn))
            .rdd
            .map(r => r.get(0).asInstanceOf[Double])
        // NOTE: testSparkDataFrame/testDataFrame will not be used further
        testSparkDataFrame.unpersist()

        // Compute metrics for current fold
        val metrics = new RegressionMetrics(predictions zip observations)
        foldMetrics +=
          RegressionMetricsRow(
            (splitIndex + 1).toString,
            training.count().toString,
            test.count().toString,
            metrics.explainedVariance,
            metrics.meanAbsoluteError,
            metrics.meanSquaredError,
            metrics.r2,
            metrics.rootMeanSquaredError)
    }
    rddWithIndex.unpersist()

    // Prepare summary row
    foldMetrics += averageRegressionMetricsRow(foldMetrics.toList)

    // Prepare Cross-validation Regression report
    val table = Table(
      this.name,
      "Cross-validate report table",
      Some(CrossValidateRegressor.reportColumnNames),
      Some(foldMetrics.map(m => m.foldNumber).toList),
      foldMetrics.map(m => m.toRowList).toList)
    new Report(ReportContent(this.name, tables = Map(table.name -> table)))
  }

  def averageRegressionMetricsRow(others: List[RegressionMetricsRow]): RegressionMetricsRow = {
    val listSize = others.size
    others.foldLeft(new RegressionMetricsRow("", "", "", 0.0, 0.0, 0.0, 0.0, 0.0)) {
      (acc, c) =>
        new RegressionMetricsRow(
          "average",
          "",
          "",
          acc.explainedVariance + c.explainedVariance / listSize,
          acc.meanAbsoluteError + c.meanAbsoluteError / listSize,
          acc.meanSquaredError + c.meanSquaredError / listSize,
          acc.r2 + c.r2 / listSize,
          acc.rootMeanSquaredError + c.rootMeanSquaredError / listSize)
    }
  }

  override protected def _inferKnowledge(context: InferContext)(
      trainableKnowledge: DKnowledge[Trainable],
      dataframeKnowledge: DKnowledge[DataFrame]): (DKnowledge[Scorable], DKnowledge[Report]) = {
    (DKnowledge(
      for (trainable <- trainableKnowledge.types)
      yield trainable.train.infer(context)(parametersForTrainable)(dataframeKnowledge)),
      DKnowledge(context
        .dOperableCatalog
        .concreteSubclassesInstances[Report]))
  }
}

object CrossValidateRegressor {
  val shuffleParamKey = "shuffle"
  val shuffleYes = "YES"
  val shuffleNo = "NO"

  val numOfFoldsParamKey = "numOfFolds"
  val seedParamKey = "seed"

  val reportColumnNames = List(
    "foldNumber",
    "trainSetSize",
    "testSetSize",
    "explainedVariance",
    "meanAbsoluteError",
    "meanSquaredError",
    "r2",
    "rootMeanSquaredError")
}

case class RegressionMetricsRow(
    foldNumber: String,
    trainSetSize: String,
    testSetSize: String,
    explainedVariance: Double,
    meanAbsoluteError: Double,
    meanSquaredError: Double,
    r2: Double,
    rootMeanSquaredError: Double) {

  def toRowList: List[Option[String]] = {
    List(
      Option(foldNumber),
      Option(trainSetSize),
      Option(testSetSize),
      Option(explainedVariance.toString),
      Option(meanAbsoluteError.toString),
      Option(meanSquaredError.toString),
      Option(r2.toString),
      Option(rootMeanSquaredError.toString))
  }
}