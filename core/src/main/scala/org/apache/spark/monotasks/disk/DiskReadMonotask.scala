/*
 * Copyright 2014 The Regents of The University California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.monotasks.disk

import java.io.FileInputStream
import java.nio.{ByteBuffer, ByteOrder}

import scala.util.control.NonFatal

import org.apache.spark.{Logging, TaskContextImpl}
import org.apache.spark.storage.{BlockId, StorageLevel}

/**
 * Contains the parameters and logic necessary to read a block from disk. The execute() method reads
 * a block from disk and stores the resulting data in the MemoryStore.
 */
private[spark] class DiskReadMonotask(
    context: TaskContextImpl, blockId: BlockId, val diskId: String)
  extends DiskMonotask(context, blockId) with Logging {

  override def execute(): Boolean = {
    val data = blockManager.blockFileManager.getBlockFile(blockId, diskId).map { file =>
      val stream = new FileInputStream(file)
      val channel = stream.getChannel
      try {
        val buf = ByteBuffer.allocateDirect(file.length().toInt)
        // Sets dataBuffer's internal byte order (endianness) to the byte order used by the
        // underlying platform.
        buf.order(ByteOrder.nativeOrder())
        channel.read(buf)
        buf.flip()
      } catch {
        case NonFatal(e) =>
          logError(s"Reading block $blockId from disk $diskId failed due to exception.", e)
          None
      } finally {
        channel.close()
        stream.close()
      }
    }.asInstanceOf[Option[ByteBuffer]]
    val success = data.isDefined
    if (success) {
      blockManager.cacheBytes(blockId, data.get, StorageLevel.MEMORY_ONLY_SER, true)
    }
    success
  }
}