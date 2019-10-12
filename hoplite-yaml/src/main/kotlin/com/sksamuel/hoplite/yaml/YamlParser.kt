package com.sksamuel.hoplite.yaml

import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.MapNode
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.PrimitiveNode
import com.sksamuel.hoplite.TreeNode
import com.sksamuel.hoplite.Value
import com.sksamuel.hoplite.parsers.Parser
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.events.AliasEvent
import org.yaml.snakeyaml.events.DocumentEndEvent
import org.yaml.snakeyaml.events.DocumentStartEvent
import org.yaml.snakeyaml.events.Event
import org.yaml.snakeyaml.events.MappingEndEvent
import org.yaml.snakeyaml.events.MappingStartEvent
import org.yaml.snakeyaml.events.ScalarEvent
import org.yaml.snakeyaml.events.SequenceEndEvent
import org.yaml.snakeyaml.events.SequenceStartEvent
import org.yaml.snakeyaml.events.StreamEndEvent
import org.yaml.snakeyaml.events.StreamStartEvent
import java.io.InputStream
import java.io.InputStreamReader

class YamlParser : Parser {
  override fun defaultFileExtensions(): List<String> = listOf("yml", "yaml")
  private val yaml = Yaml(SafeConstructor())
  override fun load(input: InputStream, source: String): TreeNode {
    val reader = InputStreamReader(input)
    val events = yaml.parse(reader).iterator()
    val stream = TokenStream(events)
    require(stream.next().`is`(Event.ID.StreamStart))
    require(stream.next().`is`(Event.ID.DocumentStart))
    stream.next()
    return TokenProduction(stream, source)
  }
}

interface TokenStream<T> {

  fun next(): T
  fun current(): T

  companion object {
    operator fun <T> invoke(iter: Iterator<T>) = object : TokenStream<T> {
      var current: T? = null
      override fun next(): T {
        current = iter.next()
        return current()
      }

      override fun current(): T = current ?: throw NoSuchElementException()
    }
  }
}

fun Event.id() = when (this) {
  is DocumentStartEvent -> Event.ID.DocumentStart
  is DocumentEndEvent -> Event.ID.DocumentEnd
  is MappingStartEvent -> Event.ID.MappingStart
  is MappingEndEvent -> Event.ID.MappingEnd
  is ScalarEvent -> Event.ID.Scalar
  is AliasEvent -> Event.ID.Alias
  is SequenceStartEvent -> Event.ID.SequenceStart
  is SequenceEndEvent -> Event.ID.SequenceEnd
  is StreamStartEvent -> Event.ID.StreamStart
  is StreamEndEvent -> Event.ID.StreamEnd
  else -> throw UnsupportedOperationException()
}

object TokenProduction {
  operator fun invoke(stream: TokenStream<Event>,
                      source: String): TreeNode {
    return when (val event = stream.current()) {
      is MappingStartEvent -> MapProduction(stream, source)
      is SequenceStartEvent -> SequenceProduction(stream, source)
      // https://yaml.org/refcard.html
      // Language Independent Scalar types:
      //    { ~, null }              : Null (no value).
      //    [ 1234, 0x4D2, 02333 ]   : [ Decimal int, Hexadecimal int, Octal int ]
      //    [ 1_230.15, 12.3015e+02 ]: [ Fixed float, Exponential float ]
      //    [ .inf, -.Inf, .NAN ]    : [ Infinity (float), Negative, Not a number ]
      //    { Y, true, Yes, ON  }    : Boolean true
      //    { n, FALSE, No, off }    : Boolean false
      is ScalarEvent -> {
        if (event.value == "null" && event.scalarStyle == DumperOptions.ScalarStyle.PLAIN)
          PrimitiveNode(Value.NullValue, event.startMark.toPos(source))
        else
          PrimitiveNode(Value.StringNode(event.value), event.startMark.toPos(source))
      }
      else -> throw java.lang.UnsupportedOperationException("Invalid YAML event ${stream.current().id()} at ${stream.current().startMark}")
    }
  }
}

object MapProduction {
  operator fun invoke(stream: TokenStream<Event>, source: String): TreeNode {
    require(stream.current().`is`(Event.ID.MappingStart))
    val mark = stream.current().startMark
    val obj = mutableMapOf<String, TreeNode>()
    while (stream.next().id() != Event.ID.MappingEnd) {
      require(stream.current().id() == Event.ID.Scalar)
      val field = stream.current() as ScalarEvent
      val fieldName = field.value
      stream.next()
      val value = TokenProduction(stream, source)
      obj[fieldName] = value
    }
    require(stream.current().`is`(Event.ID.MappingEnd))
    return MapNode(obj, mark.toPos(source), Value.NullValue)
  }
}

object SequenceProduction {
  operator fun invoke(stream: TokenStream<Event>, source: String): TreeNode {
    require(stream.current().`is`(Event.ID.SequenceStart))
    val mark = stream.current().startMark
    val list = mutableListOf<TreeNode>()
    var index = 0
    while (stream.next().id() != Event.ID.SequenceEnd) {
      val value = TokenProduction(stream, source)
      list.add(value)
      index++
    }
    require(stream.current().`is`(Event.ID.SequenceEnd))
    return ArrayNode(list.toList(), mark.toPos(source))
  }
}

fun Mark.toPos(source: String): Pos = Pos.LineColPos(line, column, source)
