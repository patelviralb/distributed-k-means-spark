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
local.input=wc_input
local.k=10
local.converge_dist=0.001
local.cleaned.input=cleaned_input
local.output=output
# Pseudo-Cluster Execution
hdfs.user.name=virtual-ubuntu
hdfs.input=wc_input
hdfs.k=10
hdfs.converge_dist=0.001
hdfs.output=output
# AWS EMR Execution
aws.emr.release=emr-5.32.0
aws.bucket.name=g16-cs6420-scala-k-means-clustering
aws.input=input
aws.output=output
aws.log.dir=log
aws.num.nodes=5
aws.instance.type=m4.xlarge
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
	${hadoop.root}/bin/hdfs dfs -put ${local.input}/* /user/${hdfs.user.name}/${hdfs.input}

# Removes hdfs output directory.
clean-hdfs-output:
	${hadoop.root}/bin/hdfs dfs -rm -r -f ${hdfs.output}*

# Download output from HDFS to local.
download-output-hdfs:
	mkdir ${local.output}
	${hadoop.root}/bin/hdfs dfs -get ${hdfs.output}/* ${local.output}

# Runs pseudo-clustered (ALL). ONLY RUN THIS ONCE, THEN USE: make pseudoq
# Make sure Hadoop  is set up (in /etc/hadoop files) for pseudo-clustered operation (not standalone).
# https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation
pseudo: jar stop-yarn format-hdfs init-hdfs upload-input-hdfs start-yarn clean-local-output
	spark-submit \
		--class ${job.name} \
		--master yarn \
		--executor-memory 4g \
		--driver-memory 4g \
		--conf spark.driver.memoryOverhead=2048 \
		--conf spark.executor.memoryOverhead=2048 \
		--conf spark.default.parallelism=48 \
		--deploy-mode cluster ${jar.path} ${hdfs.input} ${hdfs.k} ${hdfs.converge_dist} ${local.output}
	make download-output-hdfs

# Runs pseudo-clustered (quickie).
pseudoq: jar clean-local-output clean-hdfs-output
	spark-submit \
		--class ${job.name} \
		--master yarn \
		--executor-memory 4g \
		--driver-memory 4g \
		--conf spark.driver.memoryOverhead=2048 \
		--conf spark.executor.memoryOverhead=2048 \
		--conf spark.default.parallelism=48 \
		--deploy-mode cluster ${jar.path} ${hdfs.input} ${hdfs.k} ${hdfs.converge_dist} ${local.output}
	make download-output-hdfs

# Create S3 bucket.
make-bucket:
	aws s3 mb s3://${aws.bucket.name}

# Upload data to S3 input dir.
upload-input-aws: make-bucket
	aws s3 sync ${local.input} s3://${aws.bucket.name}/${aws.input}

# Delete S3 output dir.
delete-output-aws:
	aws s3 rm s3://${aws.bucket.name}/ --recursive --exclude "*" --include "${aws.output}*"

# Upload application to S3 bucket.
upload-app-aws:
	aws s3 cp ${jar.name} s3://${aws.bucket.name}

# Main EMR launch.
aws: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "K-Means Clustering Spark Cluster" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop Name=Spark \
		--steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${job.name}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}"] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate

# Download output from S3.
download-output-aws: clean-local-output
	mkdir ${local.output}
	aws s3 sync s3://${aws.bucket.name}/${aws.output} ${local.output}

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