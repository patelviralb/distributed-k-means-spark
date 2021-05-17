# Spark Scala Project - CS6240 (Spring 2021) - Group 16

# K-Means Clustering

### Code Authors:

- #### Keshav Chandrashekar
- #### Viral Patel

<br />

The repository contains programs which can be executed using Spark model

- [Installations and Setups](#installations-and-setups)
    - [Java](#java)
    - [Maven](#maven)
    - [Hadoop](#hadoop)
        - [Installation & Setup](#installation-&-setup)
        - [Test Setup](#test-setup)
        - [References](#hadoop-references)
    - [Scala](#scala)
        - [Installation](#scala-installation)
    - [Spark](#spark)
        - [Installation](#spark-installation)
        - [Setup](#spark-setup)
        - [References](#spark-references)
    - [AWS CLI](#aws-cli)
        - [Installation](#installation)
        - [Configuration](#configuration)
        - [References](#aws-references)
    - [Distributed K-Means Program Execution](#distributed-k-means-program-execution)
        - [Run in Standalone mode](#spr-run-in-standalone-mode)
        - [Run in Pseudo-Distributed mode](#spr-run-in-pseudo-distributed-mode)
        - [Run in AWS EMR](#spr-run-in-aws-emr)

<br />

# **Installations and Setups**

Below components are installed:

- JDK version `8`
- Maven
- Hadoop version `2.10.1`
- Scala version `2.11.12`
- Spark version `2.4.7`
- AWS CLI version `2`

<br />

## **JAVA**

Install JDK version 8 using below steps:

- Download lates Compressed Archive
  from [Java SE Development Kit 8 Downloads](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html)
  page

- Extract the files using command:
    ```shell
    $ sudo tar zxvf jdk-8u281-linux-x64.tar.gz -C /usr/lib/jvm
    ```

- Update any existing java with downloaded version
    ```shell
    $ sudo update-alternatives --install "/usr/bin/java" "java" "/usr/lib/jvm/jdk1.8.0_281/bin/java" 1
    ```

- Update the alternative
    ```shell
    $ sudo update-alternatives --set java /usr/lib/jvm/jdk1.8.0_281/bin/java
    ```
  **or**
    ```shell
    $ update-alternatives --config java
    ```
  and specifying correct number

- Define the Java environment variables by adding the following content to the end of
  the `~/.bashrc` file (in case you're using `zsh` use `~/.zshrc`)
    ```shell
    # Java Related Options
    export JAVA_HOME=/usr/lib/jvm/jdk1.8.0_281

    # Update PATH
    export PATH=${PATH}:${JAVA_HOME}/bin
    ```

    - Apply changes of `~/.bashrc` (in case you're using `zsh` use `~/.zshrc`)

        ```shell
        $ source ~/.bashrc
        ```

- Check `java` version to confirm installation
    ```shell
    $ java -version

    java version "1.8.0_281"
    Java(TM) SE Runtime Environment (build 1.8.0_281-b09)
    Java HotSpot(TM) 64-Bit Server VM (build 25.281-b09, mixed mode)
    ```

- Check `javac` version to confirm installation
    ```shell
    $ javac -version
    
    javac 1.8.0_281
    ```

<br />

## **MAVEN**

- Install Maven latest version
    ```shell
    $ sudo apt install maven
    ```

- Check the maven installation
    ```shell
    $ mvn -version

    Apache Maven 3.6.3
    Maven home: /usr/share/maven
    Java version: 1.8.0_281, vendor: Oracle Corporation, runtime: /usr/lib/jvm/jdk1.8.0_281/jre
    Default locale: en_US, platform encoding: UTF-8
    OS name: "linux", version: "5.8.0-40-generic", arch: "amd64", family: "unix"
    ```

<br />

## **HADOOP**

## Installation & Setup

- Download Hadoop version `2.10.1`
  from [Hadoop Download Page](https://www.apache.org/dyn/closer.cgi/hadoop/common/hadoop-2.10.1/hadoop-2.10.1.tar.gz)
    ```shell
    $ wget https://mirrors.sonic.net/apache/hadoop/common/hadoop-2.10.1/hadoop-2.10.1.tar.gz
    ```

- Extract the files using command:
    ```shell
    $ sudo tar zxvf hadoop-2.10.1.tar.gz -C /usr/lib/hadoop
    ```

- Configure Hadoop Environment Variables
    - Define the Hadoop environment variables by adding the following content to the end of
      the `~/.bashrc` file (in case you're using `zsh` use `~/.zshrc`)

        ```shell
        # Hadoop Related Options
        export HADOOP_HOME=/usr/lib/hadoop/hadoop-2.10.1
        export HADOOP_INSTALL=${HADOOP_HOME}
        export HADOOP_MAPRED_HOME=${HADOOP_HOME}
        export HADOOP_COMMON_HOME=${HADOOP_HOME}
        export HADOOP_HDFS_HOME=${HADOOP_HOME}
        export YARN_HOME=${HADOOP_HOME}
        export HADOOP_COMMON_LIB_NATIVE_DIR=${HADOOP_HOME}/lib/native
        export HADOOP_OPTS="-Djava.library.path=${HADOOP_HOME}/lib/native"
 
        # Update PATH
        export PATH=${PATH}:${JAVA_HOME}/bin:${HADOOP_HOME}/sbin:${HADOOP_HOME}/bin
        ```

    - Apply changes of `~/.bashrc` (in case you're using `zsh` use `.zshrc`)
        ```shell
        $ source ~/.bashrc
        ```

- Configure `JAVA_HOME` in `hadoop-env.sh`
    ```shell
    $ sudo vi $HADOOP_HOME/etc/hadoop/hadoop-env.sh
    ```
    - Update parameter `JAVA_HOME`

        ```shell
        # set to the root of your Java installation
        export JAVA_HOME=/usr/lib/jvm/jdk1.8.0_281
        ```

- Enable Passwordless SSH for Hadoop User
    - Install following

        ```shell
        $ sudo apt-get install ssh
        $ sudo apt-get install rsync
        ```

    - Generate an SSH key pair and define the location to be stored in
        ```shell
        $ ssh-keygen -t rsa -P '' -f ~/.ssh/hadoop_ssh
        ```

    - Use the cat command to store the public key as authorized_keys in the ssh directory
        ```shell
        $ cat ~/.ssh/hadoop_ssh.pub >> ~/.ssh/authorized_keys
        ```

    - Set the permissions for your user with the `chmod` command
        ```shell
        $ chmod 0600 ~/.ssh/authorized_keys
        ```

    - User is now able to SSH without needing to enter a password every time. Verify everything is
      set up correctly by using the hdoop user to SSH to localhost
        ```shell
        $ ssh localhost
        ```

### **Optional Steps**

- Pseudo-Distributed Mode Configurations
    - Edit `core-site.xml` File

        ```shell
        $ sudo vi $HADOOP_HOME/etc/hadoop/core-site.xml
        ```
        - Update the Configuration with below paramters

            ```xml
            <configuration>
                <property>
                        <name>hadoop.tmp.dir</name>
                        <value>/home/[username]/hadoop_files/tmpdata</value>
                </property>
                <property>
                        <name>fs.default.name</name>
                        <value>hdfs://127.0.0.1:9000</value>
                </property>
            </configuration>
            ```
        - Make sure to create a tmp directory

            ```shell
            $ sudo mkdir -p /home/[username]/hadoop_files/tmpdata
            ```
    - Edit `hdfs-site.xml` File

        ```shell
        $ sudo vi $HADOOP_HOME/etc/hadoop/hdfs-site.xml
        ```
        - Update the Configuration with below paramters

            ```xml
            <configuration>
                <property>
                    <name>dfs.namenode.name.dir</name>
                    <value>/home/[username]/hadoop_files/dfsdata/namenode</value>
                </property>
                <property>
                    <name>dfs.datanode.data.dir</name>
                    <value>/home/[username]/hadoop_files/dfsdata/datanode</value>
                </property>
                <property>
                    <name>dfs.replication</name>
                    <value>1</value>
                </property>
            </configuration>
            ```
        - Make sure to create a tmp directory

            ```shell
            $ sudo mkdir -p /home/[username]/hadoop_files/dfsdata/namenode /home/[username]/hadoop_files/dfsdata/datanode
            ```
    - Edit `mapred-site.xml` File

        ```shell
        $ sudo vi $HADOOP_HOME/etc/hadoop/mapred-site.xml
        ```
        - Update the Configuration with below paramters

            ```xml
            <configuration>
                <property>
                        <name>mapreduce.framework.name</name>
                        <value>yarn</value>
                </property>
                <property>
                        <name>mapreduce.application.classpath</name>
                        <value>${HADOOP_MAPRED_HOME}/share/hadoop/mapreduce/*:${HADOOP_MAPRED_HOME}/share/hadoop/mapreduce/lib/*</value>
                </property>
            </configuration>
            ```
    - Edit `yarn-site.xml` File

        ```shell
        $ sudo vi $HADOOP_HOME/etc/hadoop/yarn-site.xml
        ```
        - Update the Configuration with below paramters

            ```xml
            <configuration>
                <property>
                    <name>yarn.nodemanager.aux-services</name>
                    <value>mapreduce_shuffle</value>
                </property>
                <property>
                    <name>yarn.nodemanager.aux-services.mapreduce.shuffle.class</name>
                    <value>org.apache.hadoop.mapred.ShuffleHandler</value>
                </property>
                <property>
                    <name>yarn.resourcemanager.hostname</name>
                    <value>127.0.0.1</value>
                </property>
                <property>
                    <name>yarn.acl.enable</name>
                    <value>0</value>
                </property>
                <property>
                    <name>yarn.nodemanager.env-whitelist</name>
                    <value>JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PERPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_MAPRED_HOME</value>
                </property>
            </configuration>
            ```

## Test Setup

- Format HDFS NameNode
    ```shell
    $ hdfs namenode -format
    ```

- Start Hadoop Cluster
    ```shell
    $ start-dfs.sh
    ```

- Start the YARN resource and nodemanagers
    ```shell
    $ start-yarn.sh
    ```

- Check if all the daemons are active and running as Java processes
    ```shell
    $ ${JAVA_HOME}/bin/jps
    ```

- Access `Hadoop NameNode UI`
    ```
    http://localhost:50070/
    ```

- Access `Hadoop Individual DataNodes`
    ```
    http://localhost:50075/
    ```

- Access `YARN Resource Manager`
    ```
    http://localhost:8088
    ```

- Make the HDFS directories required to execute MapReduce jobs:
    ```shell
    $ hdfs dfs -mkdir /user
    $ hdfs dfs -mkdir /user/<username>
    ```

- Copy the input files into the distributed filesystem:
    ```shell
    $ hdfs dfs -mkdir input
    $ hdfs dfs -put ${HADOOP_HOME}/etc/hadoop/*.xml input
    ```

<h2 id="hadoop-references">References</h2>

- [Hadoop: Setting up a Single Node Cluster](https://hadoop.apache.org/docs/r2.10.1/hadoop-project-dist/hadoop-common/SingleCluster.html)
- [How to Install Hadoop on Ubuntu 18.04 or 20.04](https://phoenixnap.com/kb/install-hadoop-ubuntu)

<br />

## **SCALA**

<h2 id="scala-installation">Installation</h2>

- Download archive from
  link [scala-2.11.12.tgz](https://downloads.lightbend.com/scala/2.11.12/scala-2.11.12.tgz)
    ```shell
    $ wget https://downloads.lightbend.com/scala/2.11.12/scala-2.11.12.tgz
    ```

- Extract package
    ```shell
    $ sudo tar zxvf scala-2.11.12.tgz -C /usr/lib/scala
    ```

- Make symbolic link for `scala`
    ```shell
    $ sudo ln -s /usr/lib/scala/scala-2.11.12/bin/scala /usr/local/bin/scala
    ```

  ### **OR**

- Define the Scala environment variables by adding the following content to the end of
  the `~/.bashrc` file (in case you're using `zsh` use `.zshrc`)
    ```shell
        # Scala Related Options
        export SCALA_HOME=/usr/lib/scala/scala-2.11.12

        # Update PATH
        export PATH=${PATH}:${JAVA_HOME}/bin:${HADOOP_HOME}/sbin:${HADOOP_HOME}/bin:${SCALA_HOME}/bin
    ```

    - Apply changes of `~/.bashrc` (in case you're using `zsh` use `.zshrc`)
        ```shell
        $ source ~/.bashrc
        ```

- Check `scala` version to confirm installation
    ```shell
    $ scala -version

    Scala code runner version 2.11.12 -- Copyright 2002-2017, LAMP/EPFL
    ```

<br />

## **SPARK**

<h2 id="spark-installation">Installation</h2>

- Download archive from
  link [spark-2.4.7-bin-without-hadoop.tgz](https://downloads.apache.org/spark/spark-2.4.7/spark-2.4.7-bin-without-hadoop.tgz)
    ```shell
    $ wget https://downloads.apache.org/spark/spark-2.4.7/spark-2.4.7-bin-without-hadoop.tgz
    ```

- Extract package
    ```shell
    $ sudo tar xzf spark-2.4.7-bin-without-hadoop.tgz -C /usr/lib/spark
    ```

<h2 id="spark-setup">Setup</h2>

- Define the Spark environment variables by adding the following content to the end of
  the `~/.bashrc` file (in case you're using `zsh` use `.zshrc`)
    ```shell
    # Spark Related Options
    export SPARK_HOME=/usr/lib/spark/spark-2.4.7-bin-without-hadoop
    export PYSPARK_PYTHON=/usr/bin/python3
    export SPARK_DIST_CLASSPATH=$(/usr/lib/hadoop/hadoop-2.10.1/bin/hadoop classpath)
	export HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
	export YARN_CONF_DIR="${HADOOP_HOME}/etc/hadoop"

    # Update PATH
    export PATH=${PATH}:${JAVA_HOME}/bin:${HADOOP_HOME}/sbin:${HADOOP_HOME}/bin:${SCALA_HOME}/bin:${SPARK_HOME}/bin:${SPARK_HOME}/sbin
    ```

- Apply changes of `~/.bashrc` (in case you're using `zsh` use `.zshrc`)
    ```shell
    $ source ~/.bashrc
    ```

<h2 id="spark-references">References</h2>

- [How to Install Spark on Ubuntu](https://phoenixnap.com/kb/install-spark-on-ubuntu)

<br />

## **AWS CLI**

## Installation

- **For the latest version of the AWS CLI**, use the following command block:
    ```shell
    $ curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    $ unzip awscliv2.zip
    $ sudo ./aws/install
    ```
- Confirm the installation
    ```shell
    $ aws --version
    ```

## Configuration

- Substituting the keys
    ```shell
    $ aws configure
      AWS Access Key ID [None]: <your-access-key-id>
      AWS Secret Access Key [None]: <your-secret-access-key>
      Default region name [None]: us-east-1
      Default output format [None]: text
    ```
- The above step should create two secure files in your `~/.aws` directory
    ```shell
    $ ls -al ~/.aws
      total 16
      drwxr-xr-x    24  virtual-ubuntu  virtual-ubuntu  4096    Feb 2   21:44   ..
      drwxrwxr-x    2   virtual-ubuntu  virtual-ubuntu  4096    Jan 31  00:07   .
      -rw-------    1   virtual-ubuntu  virtual-ubuntu  519     Jan 31  00:07   credentials
      -rw-------    1   virtual-ubuntu  virtual-ubuntu  29      Jan 30  23:50   config
    ```

- Check that they contain the correct information from configure step above
    ```shell
    $ cat ~/.aws/credentials
    $ cat ~/.aws/config
    ```

<h2 id="aws-references">References</h2>

- [Installing, updating, and uninstalling the AWS CLI version 2 on Linux](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2-linux.html)

<br />

<h2 id="distributed-k-means-program-execution">Distributed K-Means Program Execution</h2>

- Copy `Makefile` from `config/` folder:

```shell
$ cp config/distributed.k-means.Makefile Makefile 
```

- Edit `Makefile` to customize the environment

    - `hadoop.root`: Set `${HADOOP_HOME}` path
    - `spark.root`: Set `${SPARK_HOME}` path

<h2 id="spr-run-in-standalone-mode">Run in Standalone mode</h2>

- Edit `Makefile` to customize the parameters

    - `local.input=<input directory>`
    - `local.k=<cluster count>`
    - `local.converge_dist=<convergence distance>`


- Set Standalone Hadoop environment (Execute Once)

    ```shell
    $ make switch-standalone
    ```
- Start execution
    ```shell
    $ make local
    ```

<h2 id="spr-run-in-pseudo-distributed-mode">Run in Pseudo-Distributed mode</h2>

- Update `Makefile` variable
    - `hdfs.user.name=[username]`


- Edit `Makefile` to customize the parameters

    - `hdfs.input=<input directory>`
    - `hdfs.k=<cluster count>`
    - `hdfs.converge_dist=<convergence distance>`


- Set Pseudo-Clustered Hadoop Environment (Execute Once)
    ```shell
    $ make switch-pseudo
    ```

- Start execution (First run only)
    ```shell
    $ make pseudo
    ```
- For every subsequent run use below command since `namenode` and `datanode` already running
    ```shell
    $ make pseudoq
    ```

<h2 id="spr-run-in-aws-emr">Run in AWS EMR</h2>

- Update `Makefile` variables
    - `aws.emr.release=emr-5.32.0` (Or EMR version of your choice)
    - `aws.bucket.name=[your-aws-s3-bucket-name]`
    - `aws.subnet.id=[your-aws-subnet-id]`
    - `aws.instance.type=m4.large` (Or Instance of your choice)


- Edit `Makefile` to customize the parameters

    - `aws.input=<input directory>`
    - `aws.k=<cluster count>`
    - `aws.converge_dist=<convergence distance>`


- Create S3 bucket
    ```shell
    $ make make-bucket
    ```

- Upload input file
    ```shell
    $ make upload-input-aws
    ```

- Start execution
    ```shell
    $ make aws
    ```
