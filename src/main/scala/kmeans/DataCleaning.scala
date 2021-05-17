package kmeans

import org.apache.log4j.{LogManager, Logger}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object DataCleaning {

    val LOGGER: Logger = LogManager.getRootLogger
    val spark: SparkSession = SparkSession
        .builder()
        .appName("DataCleaning")
        .getOrCreate()

    def main(args: Array[String]) {

        val startTime = System.nanoTime

        if (args.length != 2) {
            LOGGER.error("********** Usage: DataCleaning <input directory> <output directory> **********")
            System.exit(1)
        }

        val input = spark
            .read
            .textFile(args(0))
            .rdd
        val lines = cleanData(input)
        lines
            .coalesce(1)
            .saveAsTextFile(args(1))
        spark.stop()

        val duration = (System.nanoTime - startTime) / 1e9d
        LOGGER.info(s"---------- Program runtime:\t{${duration} second(s)} ----------")
        LOGGER.info(s"---------- Program runtime:\t{${duration / 60} minute(s)} ----------")
        LOGGER.info(s"---------- Program runtime:\t{${duration / (60 * 60)} hour(s)} ----------")
    }

    def cleanData(input: RDD[String]): RDD[String] = {
        input
            .map(line => line.split(','))
            .filter(line => parseDouble(line(1)).isDefined && parseDouble(line(2)).isDefined && parseDouble(line(3)).isDefined)
            .map(line => line(1) + "," + line(2) + "," + line(3))
    }

    def parseDouble(s: String): Option[Double] = {
        try {
            Some(s.toDouble)
        } catch {
            case _: Throwable => None
        }
    }
}
