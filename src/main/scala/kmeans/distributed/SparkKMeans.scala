package kmeans.distributed

import breeze.linalg.{DenseVector, Vector, squaredDistance}
import org.apache.log4j.{LogManager, Logger}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
 * A K Means clustering algorithm that can be distributed across available worker machines.
 */
object SparkKMeans {

    val LOGGER: Logger = LogManager.getRootLogger

    /**
     * Parses argument passed to the program
     *
     * @param args Array of String
     * @return inputDirectory String representation of input directory
     * @return kValue Int representation of K value
     * @return inputDirectory Double representation of converge distance
     * @return inputDirectory String representation of output directory
     */
    def parseArguments(args: Array[String]): (String, Int, Double, String) = {
        val inputDirectory = args(0)
        LOGGER.info(s"********** input directory value read **********")
        val kValue = args(1).toInt
        LOGGER.info(s"********** K value read **********")
        val convergeDistance = args(2).toDouble
        LOGGER.info(s"********** converge distance value read **********")
        val outputDirectory = args(3)
        LOGGER.info(s"********** output directory value read **********")

        (inputDirectory, kValue, convergeDistance, outputDirectory)
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
     * @return RDD of Double Vector representation of input data
     */
    def parseInputFile(inputDirectory: String, spark: SparkSession): RDD[Vector[Double]] = {
        val input = spark
            .read
            .textFile(inputDirectory)
            .rdd
        LOGGER.info(s"********** input RDD created **********")

        val data = input
            .map(parseVector)
        LOGGER.info(s"********** input parsed and vectors created **********")

        data
    }

    /**
     * Finds the closest centroid for current point vector
     *
     * @param point   Vector of Double representing the current point to find closest centroid
     * @param centers Array of Double Vector representing current centroids
     * @return closest centroid index
     */
    def closestPoint(point: Vector[Double], centers: Array[Vector[Double]]): Int = {
        var bestIndex = 0
        var closest = Double.PositiveInfinity

        for (i <- centers.indices) {
            val tempDist = squaredDistance(point, centers(i))
            if (tempDist < closest) {
                closest = tempDist
                bestIndex = i
            }
        }

        bestIndex
    }

    /**
     * iteratively finds centroids until convergence.
     * For distance computation, squared euclidean distance formula is used
     *
     * @param data             RDD of Double Vector representation of input data
     * @param kValue           K value
     * @param convergeDistance convergence distance to stop the iteration
     * @param kPoints          initial cluster centroids
     * @return RDD of cluster centroid and List of points belonging to the centroid
     */
    def findFinalClusterCentroids(data: RDD[Vector[Double]],
                                  kValue: Int,
                                  convergeDistance: Double,
                                  kPoints: Array[Vector[Double]])
    : RDD[(Int, Iterable[Vector[Double]])] = {
        var tempDist = 1.0
        var pointsInCluster: RDD[(Int, Iterable[Vector[Double]])] = null
        var iteration: Int = 0

        while (tempDist > convergeDistance) {
            val closest = data
                .map(point => (closestPoint(point, kPoints), (point, 1)))
            LOGGER.info(s"********** closest calculated for iteration $iteration **********")

            val pointStats = closest
                .reduceByKey {
                    case ((p1, c1), (p2, c2)) => (p1 + p2, c1 + c2)
                }
            LOGGER.info(s"********** pointStats calculated for iteration $iteration **********")

            pointsInCluster = closest
                .groupByKey()
                .mapValues(_.map(_._1))
            LOGGER.info(s"********** cluster points calculated for iteration $iteration **********")

            val newPoints = pointStats
                .map {
                    pair => (pair._1, pair._2._1 * (1.0 / pair._2._2))
                }
                .collectAsMap()
            LOGGER.info(s"********** newPoints calculated for iteration $iteration **********")

            tempDist = 0.0
            for (i <- 0 until kValue) {
                LOGGER.info(s"********** distance calculated for K $i for iteration $iteration**********")
                tempDist += squaredDistance(kPoints(i), newPoints(i))
            }

            for (newP <- newPoints) {
                LOGGER.info(s"********** centroid ${newP._1} updated for iteration $iteration **********")
                kPoints(newP._1) = newP._2
            }

            LOGGER.info(s"********** Finished iteration $iteration (delta = $tempDist) **********")
            iteration += 1
        }

        pointsInCluster
    }

    /**
     * Saves points belonging to same centroid in output file(s)
     *
     * @param pointsInCluster RDD of cluster and Vector of Double representation of points belonging to the cluster
     * @param outputDirectory String representation of output directory
     */
    def saveClustersToFile(pointsInCluster: RDD[(Int, Iterable[Vector[Double]])], outputDirectory: String): Unit = {
        pointsInCluster
            .saveAsTextFile(outputDirectory + "/clusters")
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
            LOGGER.error("********** Usage: SparkKMeans <input directory> <k> <converge distance> <output directory> **********")
            System.exit(1)
        }

        val (inputDirectory, kValue, convergeDistance, outputDirectory) = parseArguments(args)
        LOGGER.info(s"********** input directory: $inputDirectory **********")
        LOGGER.info(s"********** kValue: $kValue **********")
        LOGGER.info(s"********** convergeDistance: $convergeDistance **********")
        LOGGER.info(s"********** outputDirectory: $outputDirectory **********")

        val data = parseInputFile(inputDirectory, spark).cache()

        val kPoints = data.takeSample(withReplacement = false, kValue, 42)
        LOGGER.info(s"********** initial centroids selected **********")

        val finalClustersWithPoints = findFinalClusterCentroids(data, kValue, convergeDistance, kPoints)

        LOGGER.info(s"********** Final centers computed **********")
        println("Final centers:")
        kPoints.foreach(println)

        saveClustersToFile(finalClustersWithPoints, outputDirectory)

        spark.stop()

        val duration = (System.nanoTime - startTime) / 1e9d
        LOGGER.info(s"---------- Program runtime:\t{$duration second(s)} ----------")
        LOGGER.info(s"---------- Program runtime:\t{${duration / 60} minute(s)} ----------")
        LOGGER.info(s"---------- Program runtime:\t{${duration / (60 * 60)} hour(s)} ----------")
    }
}
