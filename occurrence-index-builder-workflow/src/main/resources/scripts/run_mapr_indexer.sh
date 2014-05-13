export HADOOP_CLIENT_OPTS="-Xmx1073741824 $HADOOP_CLIENT_OPTS"
hadoop --config /etc/hadoop/conf.cloudera.mapreduce1 jar /opt/cloudera/parcels/SOLR/lib/solr/contrib/mr/search-mr-*-job.jar org.apache.solr.hadoop.MapReduceIndexerTool -D morphlineVariable.ENV_ZK_HOST=$2 -D morphlineVariable.ENV_SOLR_COLLECTION=$3 -libjars /opt/cloudera/parcels/SOLR/lib/solr/server/webapps/solr/WEB-INF/lib/jts-1.13.jar --log4j /opt/cloudera/parcels/SOLR/share/doc/search-1.2.0/examples/solr-nrt/log4j.properties --morphline-file solr_occurrence_morphline.conf --output-dir $1 --verbose --go-live --zk-host $2 --collection $3 $4
