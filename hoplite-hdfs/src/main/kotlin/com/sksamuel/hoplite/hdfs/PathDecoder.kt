package com.sksamuel.hoplite.hdfs

import arrow.data.invalid
import arrow.data.valid
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.TreeNode
import com.sksamuel.hoplite.Value
import com.sksamuel.hoplite.decoder.DecoderRegistry
import com.sksamuel.hoplite.decoder.NonNullableDecoder
import org.apache.hadoop.fs.Path
import kotlin.reflect.KType
import kotlin.reflect.full.createType

class PathDecoder : NonNullableDecoder<Path> {

  override fun supports(type: KType): Boolean = type.classifier == Path::class

  override fun safeDecode(node: TreeNode,
                          type: KType,
                          registry: DecoderRegistry): ConfigResult<Path> {
    return when (val v = node.value) {
      is Value.StringNode -> Path(v.value).valid()
      else -> ConfigFailure.DecodeError(node, Path::class.createType()).invalid()
    }
  }
}
