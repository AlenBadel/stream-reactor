package com.landoop.streamreactor.connect.hive.sink.partitioning

import com.landoop.streamreactor.connect.hive.DatabaseName
import com.landoop.streamreactor.connect.hive.Partition
import com.landoop.streamreactor.connect.hive.TableName
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hive.metastore.IMetaStoreClient

import scala.util.Success
import scala.util.Try

/**
  * A [[PartitionHandler]] that delegates to an underlying policy
  * and caches the results for the lifetime of this object.
  */
class CachedPartitionHandler(partitioner: PartitionHandler) extends PartitionHandler {

  val cache = scala.collection.mutable.Map.empty[Partition, Path]

  override def path(
    partition: Partition,
    db:        DatabaseName,
    tableName: TableName,
  )(client:    IMetaStoreClient,
    fs:        FileSystem,
  ): Try[Path] =
    cache.get(partition) match {
      case Some(path) => Success(path)
      case _ =>
        val created = partitioner.path(partition, db, tableName)(client, fs)
        created.foreach(cache.put(partition, _))
        created
    }
}
