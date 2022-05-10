-- SORT_QUERY_RESULTS

set hive.support.concurrency=true;
set hive.txn.manager=org.apache.hadoop.hive.ql.lockmgr.DbTxnManager;
set hive.acid.direct.insert.enabled=true;
set hive.acid.createtable.softdelete=true;

DROP TABLE IF EXISTS source;

DROP TABLE IF EXISTS test_orc_ctas;

DROP TABLE IF EXISTS test_orc_mmctas;

DROP TABLE IF EXISTS test_parquet_mmctas;

DROP TABLE IF EXISTS test_avro_mmctas;

DROP TABLE IF EXISTS test_textfile_mmctas;

DROP TABLE IF EXISTS test_partition_orc_ctas;

DROP TABLE IF EXISTS test_partition_orc_mmctas;

DROP TABLE IF EXISTS test_partition_parquet_mmctas;

DROP TABLE IF EXISTS test_partition_avro_mmctas;

DROP TABLE IF EXISTS test_partition_textfile_mmctas;

CREATE TABLE source (id int, value string) CLUSTERED BY(id) INTO 10 BUCKETS STORED AS ORC TBLPROPERTIES('transactional'='true');

INSERT INTO source values ('1','one'),('2','two'),('3','three'),('4','four'),('5','five'),('6','six'),('7','seven'),('8','eight'),('9','nine'),('10','ten'),('11','eleven'),('12','twelve'),('13','thirteen'),('14','fourteen'),('15','fifteen'),('16','sixteen'),('17','seventeen'),('18','eighteen'),('19','nineteen'),('20','twenty');

CREATE TABLE IF NOT EXISTS test_orc_ctas STORED AS ORC TBLPROPERTIES('transactional'='true') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_orc_mmctas STORED AS ORC TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_parquet_mmctas STORED AS PARQUET TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_avro_mmctas STORED AS AVRO TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_textfile_mmctas STORED AS TEXTFILE TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_partition_orc_ctas PARTITIONED BY (id) STORED AS ORC TBLPROPERTIES('transactional'='true') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_partition_orc_mmctas PARTITIONED BY (id) STORED AS ORC TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_partition_parquet_mmctas PARTITIONED BY (id) STORED AS PARQUET TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_partition_avro_mmctas PARTITIONED BY (id) STORED AS AVRO TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

CREATE TABLE IF NOT EXISTS test_partition_textfile_mmctas PARTITIONED BY (id) STORED AS TEXTFILE TBLPROPERTIES('transactional'='true', 'transactional_properties'='insert_only') AS (SELECT * FROM source WHERE id = 1 UNION SELECT * FROM source WHERE id = 2);

SELECT * FROM test_orc_ctas;

SELECT * FROM test_orc_mmctas;

SELECT * FROM test_parquet_mmctas;

SELECT * FROM test_avro_mmctas;

SELECT * FROM test_textfile_mmctas;

SELECT * FROM test_partition_orc_ctas;

SELECT * FROM test_partition_orc_mmctas;

SELECT * FROM test_partition_parquet_mmctas;

SELECT * FROM test_partition_avro_mmctas;

SELECT * FROM test_partition_textfile_mmctas;

DROP TABLE IF EXISTS test_orc_ctas;

DROP TABLE IF EXISTS test_orc_mmctas;

DROP TABLE IF EXISTS test_parquet_mmctas;

DROP TABLE IF EXISTS test_avro_mmctas;

DROP TABLE IF EXISTS test_textfile_mmctas;

DROP TABLE IF EXISTS test_partition_orc_ctas;

DROP TABLE IF EXISTS test_partition_orc_mmctas;

DROP TABLE IF EXISTS test_partition_parquet_mmctas;

DROP TABLE IF EXISTS test_partition_avro_mmctas;

DROP TABLE IF EXISTS test_partition_textfile_mmctas;

