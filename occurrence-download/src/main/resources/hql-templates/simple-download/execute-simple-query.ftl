<#--
  This is a freemarker template which will generate an HQL script which is run at download time.
  When run in Hive as a parameterized query, this will create a set of tables ...
  TODO: document when we actually know something accurate to write here...
-->
<#-- Required syntax to escape Hive parameters. Outputs "USE ${hiveDB};" -->
USE ${r"${hiveDB}"};

-- setup for our custom, combinable deflated compression
SET hive.exec.compress.output=true;
SET io.seqfile.compression.type=BLOCK;
SET mapred.output.compression.codec=org.gbif.hadoop.compress.d2.D2Codec;
SET io.compression.codecs=org.gbif.hadoop.compress.d2.D2Codec;

-- in case this job is relaunched
DROP TABLE IF EXISTS ${r"${occurrenceTable}"};

-- pre-create verbatim table so it can be used in the multi-insert
CREATE TABLE ${r"${occurrenceTable}"} ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
AS SELECT
<#list fields as field>
  ${field.hiveField}<#if field_has_next>,</#if>
</#list>
FROM occurrence_download
WHERE ${r"${whereClause}"};