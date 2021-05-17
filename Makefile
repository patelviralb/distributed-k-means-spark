# Makefile for Spark WordCount project.

# Customize these paths for your environment.
# -----------------------------------------------------------
spark.root=/usr/lib/spark/spark-2.4.7-bin-without-hadoop
hadoop.root=/usr/lib/hadoop/hadoop-2.10.1
app.name=SparkKMeans
jar.name=k-means-clustering-1.0.jar
jar.path=target/${jar.name}
job.name=kmeans.distributed.SparkKMeans
# Standalone Execution
local.master=local[4]
local.io.type=100
local.input=${local.io.type}_input
local.k=cleaned
local.converge_dist=0.001
local.output=${local.io.type}_output
local.cleaned.input=full_input
local.spark.default.parallelism=48
# Pseudo-Cluster Execution
hdfs.user.name=virtual-ubuntu
hdfs.io.type=full
hdfs.input=${hdfs.io.type}_input
hdfs.k=10
hdfs.converge_dist=0.001
hdfs.output=${hdfs.io.type}_output
hdfs.spark.default.parallelism=48
# AWS EMR Execution
aws.emr.release=emr-5.32.0
aws.bucket.name=g16-cs6420-scala-distributed-k-means-clustering
aws.instance.type=m4.xlarge
aws.executor.memory="8g"
aws.driver.memory="8g"
aws.driver.memory.overhead="4g"
aws.executor.memory.overhead="4g"
aws.num.nodes=5
aws.io.type=30M
aws.input=${aws.io.type}_input
aws.k=20
aws.converge_dist=0.5
aws.output=${aws.instance.type}_${aws.io.type}_output
aws.log.dir=log
aws.spark.default.parallelism=200
aws.spark.sql.shuffle.partitions=200
# Distribution
distribution.name=KMeansClustering
# -----------------------------------------------------------

# Compiles code and builds jar (with dependencies)
jar:
	mvn clean package

# Removes local output directory
clean-local-output:
	rm -rf ${local.output}*

# Runs standalone
local: jar clean-local-output
	spark-submit \
		--class ${job.name} \
		--master ${local.master} \
		--executor-memory 4g \
		--driver-memory 4g \
		--conf spark.driver.memoryOverhead=2048 \
		--conf spark.executor.memoryOverhead=2048 \
		--conf spark.default.parallelism=${local.spark.default.parallelism} \
		--name "${app.name}" ${jar.path} ${local.input} ${local.k} ${local.converge_dist} ${local.output}

# Clean input locally
clean-input: jar
	spark-submit \
		--class ${job.name} \
		--master ${local.master} \
		--name "${app.name}" ${jar.path} ${local.input} ${local.cleaned.input}

# Start HDFS
start-hdfs:
	${hadoop.root}/sbin/start-dfs.sh

# Stop HDFS
stop-hdfs:
	${hadoop.root}/sbin/stop-dfs.sh

# Start YARN
start-yarn: stop-yarn
	${hadoop.root}/sbin/start-yarn.sh

# Stop YARN
stop-yarn:
	${hadoop.root}/sbin/stop-yarn.sh

# Reformats & initializes HDFS.
format-hdfs: stop-hdfs
	rm -rf /tmp/hadoop*
	${hadoop.root}/bin/hdfs namenode -format

# Initializes user & input directories of HDFS.
init-hdfs: start-hdfs
	${hadoop.root}/bin/hdfs dfs -rm -r -f /user
	${hadoop.root}/bin/hdfs dfs -mkdir /user
	${hadoop.root}/bin/hdfs dfs -mkdir /user/${hdfs.user.name}
	${hadoop.root}/bin/hdfs dfs -mkdir /user/${hdfs.user.name}/${hdfs.input}

# Load data to HDFS
upload-input-hdfs: start-hdfs
	${hadoop.root}/bin/hdfs dfs -put ${hdfs.input}/* /user/${hdfs.user.name}/${hdfs.input}

# Removes hdfs output directory.
clean-hdfs-output:
	${hadoop.root}/bin/hdfs dfs -rm -r -f ${hdfs.output}*

# Download output from HDFS to local.
download-output-hdfs:
	rm -rf ${hdfs.output}
	mkdir ${hdfs.output}
	${hadoop.root}/bin/hdfs dfs -get ${hdfs.output}/* ${hdfs.output}

# Runs pseudo-clustered (ALL). ONLY RUN THIS ONCE, THEN USE: make pseudoq
# Make sure Hadoop  is set up (in /etc/hadoop files) for pseudo-clustered operation (not standalone).
# https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation
pseudo: jar stop-yarn format-hdfs init-hdfs upload-input-hdfs start-yarn clean-hdfs-output
	spark-submit \
		--class ${job.name} \
		--master yarn \
		--executor-memory 2g \
		--driver-memory 2g \
		--conf spark.driver.memoryOverhead=1024 \
		--conf spark.executor.memoryOverhead=1024 \
		--conf spark.default.parallelism=${hdfs.spark.default.parallelism} \
		--deploy-mode cluster ${jar.path} ${hdfs.input} ${hdfs.k} ${hdfs.converge_dist} ${hdfs.output}
	make download-output-hdfs

# Runs pseudo-clustered (quickie).
pseudoq: jar clean-hdfs-output
	spark-submit \
		--class ${job.name} \
		--master yarn \
		--executor-memory 2g \
		--driver-memory 2g \
		--conf spark.driver.memoryOverhead=1024 \
		--conf spark.executor.memoryOverhead=1024 \
		--conf spark.default.parallelism=${hdfs.spark.default.parallelism} \
		--deploy-mode cluster ${jar.path} ${hdfs.input} ${hdfs.k} ${hdfs.converge_dist} ${hdfs.output}
	make download-output-hdfs

# Create S3 bucket.
make-bucket:
	aws s3 mb s3://${aws.bucket.name}

# Upload data to S3 input dir.
upload-input-aws:
	aws s3 sync ${aws.input} s3://${aws.bucket.name}/${aws.input}

# Upload application to S3 bucket.
upload-app-aws:
	aws s3 cp ${jar.path} s3://${aws.bucket.name}

# Main EMR launch.
aws: jar upload-app-aws
	aws emr create-cluster \
		--name "Distributed K-Means Clustering Spark ${aws.instance.type} Cluster - 1 Master ${aws.num.nodes} Cores - ${aws.io.type} Input" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop Name=Spark \
		--steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${job.name}","--executor-memory","${aws.executor.memory}","--driver-memory","${aws.driver.memory}","--conf","spark.driver.memoryOverhead=${aws.driver.memory.overhead}","--conf","spark.executor.memoryOverhead=${aws.executor.memory.overhead}","--conf","spark.default.parallelism=${aws.spark.default.parallelism}","--conf","spark.sql.shuffle.partitions=${aws.spark.sql.shuffle.partitions}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","${aws.k}","${aws.converge_dist}","s3://${aws.bucket.name}/${aws.output}"] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate

# Download output from S3.
download-output-aws:
	rm -rf ${aws.output}*
	mkdir ${aws.output}
	aws s3 sync s3://${aws.bucket.name}/${aws.output} ${aws.output}

# Change to standalone mode.
switch-standalone:
	cp config/standalone/*.xml ${hadoop.root}/etc/hadoop

# Change to pseudo-cluster mode.
switch-pseudo:
	cp config/pseudo/*.xml ${hadoop.root}/etc/hadoop

# Package for release.
distro:
	rm -f ${distribution.name}.tar.gz
	rm -f ${distribution.name}.zip
	rm -rf build
	mkdir -p build/deliv/${distribution.name}
	cp -r src build/deliv/${distribution.name}
	cp -r config build/deliv/${distribution.name}
	cp -r input build/deliv/${distribution.name}
	cp pom.xml build/deliv/${distribution.name}
	cp Makefile build/deliv/${distribution.name}
	cp README.txt build/deliv/${distribution.name}
	tar -czf ${distribution.name}.tar.gz -C build/deliv ${distribution.name}
	cd build/deliv && zip -rq ../../${distribution.name}.zip ${distribution.name}