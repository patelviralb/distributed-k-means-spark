package kmeans.local

import breeze.linalg.{DenseVector, Vector, squaredDistance}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat
import org.apache.log4j.{LogManager, Logger}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import java.util.Random
import scala.collection.immutable.ListMap
import scala.collection.mutable

/**
 * A K Means clustering algorithm that runs on single machine for each individual value of K.
 */
object LocalKMeans {

    val LOGGER: Logger = LogManager.getRootLogger

    /**
     * Parses argument passed to the program
     *
     * @param args Array of String
     * @return inputDirectory String representation of input directory
     * @return kValuesDirectory String representation of directory containing list of K values
     * @return inputDirectory Double representation of converge distance
     * @return inputDirectory String representation of output directory
     */
    def parseArguments(args: Array[String]): (String, String, Double, String) = {
        val inputDirectory = args(0)
        LOGGER.info(s"********** input directory value read **********")
        val kValuesDirectory = args(1)
        LOGGER.info(s"********** K directory value read **********")
        val convergeDistance = args(2).toDouble
        LOGGER.info(s"********** converge distance value read **********")
        val outputDirectory = args(3)
        LOGGER.info(s"********** output directory value read **********")

        (inputDirectory, kValuesDirectory, convergeDistance, outputDirectory)
    }

    /**
     * Create RDD of K values from file
     *
     * @param kValuesDirectory String representation of directory containing file with K values
     * @param spark            SparkSession
     * @return RDD of String containing K values
     */
    def readKValuesFromFile(kValuesDirectory: String, spark: SparkSession): RDD[String] = {
        val conf = new Configuration(spark.sparkContext.hadoopConfiguration)

        val lines = spark
            .sparkContext
            .newAPIHadoopFile(
                kValuesDirectory,
                classOf[NLineInputFormat],
                classOf[LongWritable],
                classOf[Text],
                conf)
        val kValues = lines
            .map {
                case (_: LongWritable, t2: Text) => t2.toString
            }

        kValues
    }

    /**
     * Parses input data line and creates Vector of Double value
     *
     * @param line input data line
     * @return Double Vector representation of input line
     */
    def parseVector(line: String): Vector[Double] = DenseVector(line.split(',').map(_.toDouble))

    /**
     * Parses input file present in input directory
     *
     * @param inputDirectory String representation of input directory
     * @param spark          SparkSession
     * @return Array of Double Vector representation of input data
     */
    def parseInputFile(inputDirectory: String, spark: SparkSession): Array[Vector[Double]] = {
        val input = spark
            .read
            .textFile(inputDirectory)
            .rdd
        LOGGER.info(s"********** input RDD created **********")

        val data = input
            .map(parseVector)
            .collect()
        LOGGER.info(s"********** input parsed and vectors created **********")

        data
    }

    /**
     * Finds the closest centroid for current point vector
     *
     * @param point   Vector of Double representing the current point to find closest centroid
     * @param centers HashMap of centroid index and Double Vector representing points in current centroids
     * @return closest centroid index
     */
    def closestPoint(point: Vector[Double], centers: mutable.HashMap[Int, Vector[Double]]): Int = {
        var bestIndex = 0
        var closest = Double.PositiveInfinity

        for (i <- 1 to centers.size) {
            val currentCenter = centers(i)
            val tempDist = squaredDistance(point, currentCenter)
            if (tempDist < closest) {
                closest = tempDist
                bestIndex = i
            }
        }

        bestIndex
    }

    /**
     * Performs K-Means computation for given value of K
     *
     * @param kValue           Integer value of K
     * @param data             Array of Double Vector representing points
     * @param convergeDistance convergence distance to stop the iteration
     * @return String representation of Map representing the K centroid and points belonging to those centroids
     */
    def performKMeans(kValue: Int, data: Array[Vector[Double]], convergeDistance: Double): (String, String) = {
        LOGGER.info(s"********** started performKMeans for K = ${kValue} **********")

        val rand = new Random(42)

        val points = new mutable.HashSet[Vector[Double]]
        val kPoints = new mutable.HashMap[Int, Vector[Double]]
        var tempDist = 1.0
        var pointsInCluster: Map[Int, Array[Vector[Double]]] = null
        var iteration: Int = 0
        var SSE: Double = 0.0

        // Get initial K centroids randomly from data
        while (points.size < kValue) {
            points.add(data(rand.nextInt(data.length)))
        }
        LOGGER.info(s"********** initial centroids selected **********")

        // Create a HashMap of initial centroids
        val iter = points.iterator
        for (i <- 1 to points.size) {
            kPoints.put(i, iter.next())
        }
        LOGGER.info(s"********** HashMap created with initial centroids **********")

        while (tempDist > convergeDistance) {
            val closest = data
                .map(point => (closestPoint(point, kPoints), (point, 1)))
            LOGGER.info(s"********** closest calculated for iteration $iteration : k $kValue **********")

            val pointsMapping = closest
                .groupBy[Int](centroidPoints => centroidPoints._1)
            LOGGER.info(s"********** cluster points grouped for iteration $iteration : k $kValue **********")

            pointsInCluster = pointsMapping
                .map(clusterData => {
                    val clusterId = clusterData._1
                    val value = clusterData._2
                    val clusterPoints = new Array[Vector[Double]](value.length)
                    for (i <- value.indices) {
                        clusterPoints(i) = value(i)._2._1
                    }
                    (clusterId, clusterPoints)
                }
                )
            LOGGER.info(s"********** cluster points calculated for iteration $iteration : k $kValue **********")

            val pointStats = pointsMapping
                .map { pair =>
                    pair._2.reduceLeft[(Int, (Vector[Double], Int))] {
                        case ((id1, (p1, c1)), (_, (p2, c2))) => (id1, (p1 + p2, c1 + c2))
                    }
                }
            LOGGER.info(s"********** pointStats calculated for iteration $iteration : k $kValue **********")

            val newPoints = pointStats
                .map {
                    mapping =>
                        (mapping._1, mapping._2._1 * (1.0 / mapping._2._2))
                }
            LOGGER.info(s"********** newPoints calculated for iteration $iteration : k $kValue **********")

            tempDist = 0.0
            for (mapping <- newPoints) {
                tempDist += squaredDistance(kPoints(mapping._1), mapping._2)
            }
            LOGGER.info(s"********** distance calculated for iteration $iteration : k $kValue **********")

            for (newP <- newPoints) {
                LOGGER.info(s"********** centroid ${newP._1} updated for iteration $iteration : k $kValue **********")
                kPoints.put(newP._1, newP._2)
            }

            LOGGER.info(s"********** Finished iteration $iteration (delta = $tempDist) **********")
            iteration += 1
        }

        LOGGER.info(s"********** Final SSE value for k=$kValue is: $SSE **********")
        println(s"=" * 100)

        (
            ListMap(pointsInCluster
                .map(clusterData => (clusterData._1, clusterData._2.mkString(" | ")))
                .toSeq
                .sortBy(_._1): _*
            ).mkString("\n"),
            s"********** Final centers computed for k=${kValue} **********\n${kPoints.mkString("\n")}"
        )
    }

    /**
     * Main driver program
     *
     * @param args command line argument
     */
    def main(args: Array[String]) {

        val startTime = System.nanoTime

        val spark: SparkSession = SparkSession
            .builder()
            .appName("SparkKMeans")
            .getOrCreate()

        if (args.length < 4) {
            LOGGER.error("Usage: SparkKMeans <input directory> <file with list of k values> <converge distance> <output directory>")
            System.exit(1)
        }

        val (inputDirectory, kValuesDirectory, convergeDistance, outputDirectory) = parseArguments(args)
        LOGGER.info(s"********** input directory: $inputDirectory **********")
        LOGGER.info(s"********** kValue: $kValuesDirectory **********")
        LOGGER.info(s"********** convergeDistance: $convergeDistance **********")
        LOGGER.info(s"********** outputDirectory: $outputDirectory **********")

        spark.conf.set("mapreduce.input.lineinputformat.linespermap", 1)

        val kValues = readKValuesFromFile(kValuesDirectory, spark)

        val data = parseInputFile(inputDirectory, spark)
        kValues.sparkContext.broadcast(data)

        val centroidData = kValues
            .map(kValue => performKMeans(kValue.toInt, data, convergeDistance))
        centroidData
            .map(currentCentroidData => currentCentroidData._1)
            .saveAsTextFile(outputDirectory)

        println(
            centroidData
                .map(currentCentroidData => currentCentroidData._2)
                .collect()
                .mkString("\n")
        )
        spark.stop()

        val duration = (System.nanoTime - startTime) / 1e9d
        LOGGER.info(s"---------- Program runtime:\t{$duration second(s)} ----------")
        LOGGER.info(s"---------- Program runtime:\t{${duration / 60} minute(s)} ----------")
        LOGGER.info(s"---------- Program runtime:\t{${duration / (60 * 60)} hour(s)} ----------")
    }
}