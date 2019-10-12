package com.sksamuel.hoplite.decoder

import arrow.data.invalid
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.PrimitiveNode
import com.sksamuel.hoplite.TreeNode
import com.sksamuel.hoplite.Value
import com.sksamuel.hoplite.arrow.flatMap
import com.sksamuel.hoplite.arrow.sequence
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

class SetDecoder : NonNullableDecoder<Set<*>> {

  override fun supports(type: KType): Boolean = type.isSubtypeOf(Set::class.starProjectedType)

  override fun safeDecode(node: TreeNode,
                          type: KType,
                          registry: DecoderRegistry): ConfigResult<Set<*>> {
    require(type.arguments.size == 1)

    val t = type.arguments[0].type!!

    fun <T> decode(node: ArrayNode, decoder: Decoder<T>): ConfigResult<Set<T>> {
      return node.elements.map { decoder.decode(it, type, registry) }.sequence()
        .leftMap { ConfigFailure.CollectionElementErrors(node, it) }
        .map { it.toSet() }
    }

    fun <T> decode(node: PrimitiveNode, value: String, decoder: Decoder<T>): ConfigResult<Set<T>> {
      val tokens = value.split(",").map {
        PrimitiveNode(Value.StringNode(it.trim()), node.pos)
      }
      return tokens.map { decoder.decode(it, type, registry) }.sequence()
        .leftMap { ConfigFailure.CollectionElementErrors(node, it) }
        .map { it.toSet() }
    }

    return registry.decoder(t).flatMap { decoder ->
      when (node) {
        is ArrayNode -> decode(node, decoder)
        is PrimitiveNode -> when (val v = node.value) {
          is Value.StringNode -> decode(node, v.value, decoder)
          else -> ConfigFailure.UnsupportedCollectionType(node, "Set").invalid()
        }
        else -> ConfigFailure.UnsupportedCollectionType(node, "Set").invalid()
      }
    }
  }
}
