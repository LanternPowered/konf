/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uchuhimo.konf.source.base

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ContainerNode
import com.uchuhimo.konf.ListNode
import com.uchuhimo.konf.TreeNode
import com.uchuhimo.konf.ValueNode
import com.uchuhimo.konf.notEmptyOr
import com.uchuhimo.konf.source.ListSourceNode
import com.uchuhimo.konf.source.Source
import com.uchuhimo.konf.source.SourceInfo
import com.uchuhimo.konf.source.asTree
import java.util.Collections

/**
 * Source from a map in flat format.
 */
open class FlatSource(
    val map: Map<String, String>,
    type: String = "",
    final override val info: SourceInfo = SourceInfo()
) : Source {
    init {
        info["type"] = type.notEmptyOr("flat")
    }

    override val tree: TreeNode = ContainerNode(mutableMapOf()).apply {
        map.forEach { (path, value) ->
            set(path, value.asTree())
        }
    }.promoteToList()
}

object EmptyStringNode : ValueNode, ListNode {
    override val children: MutableMap<String, TreeNode> = Collections.unmodifiableMap(mutableMapOf())
    override val value: Any = ""
    override val list: List<TreeNode> = listOf()
}

class SingleStringListNode(override val value: String) : ValueNode, ListNode {
    override val children: MutableMap<String, TreeNode> = Collections.unmodifiableMap(
        mutableMapOf("0" to value.asTree()))
    override val list: List<TreeNode> = listOf(value.asTree())
}

fun ContainerNode.promoteToList(): TreeNode {
    for ((key, child) in children) {
        if (child is ContainerNode) {
            children[key] = child.promoteToList()
        } else if (child is ValueNode) {
            val value = child.value
            if (value is String) {
                if (',' in value) {
                    children[key] = ListSourceNode(value.split(',').map { it.asTree() })
                } else if (value == "") {
                    children[key] = EmptyStringNode
                } else {
                    children[key] = SingleStringListNode(value)
                }
            }
        }
    }
    val list = generateSequence(0) { it + 1 }.map {
        val key = it.toString()
        if (key in children) key else null
    }.takeWhile {
        it != null
    }.filterNotNull().toList()
    if (list.isNotEmpty() && list.toSet() == children.keys) {
        return ListSourceNode(list.map { children[it]!! })
    } else {
        return this
    }
}

/**
 * Returns a map in flat format for this config.
 *
 * The returned map contains all items in this config.
 * This map can be loaded into config as [com.uchuhimo.konf.source.base.FlatSource] using
 * `config.from.map.flat(map)`.
 */
fun Config.toFlatMap(): Map<String, String> {
    fun MutableMap<String, String>.putFlat(key: String, value: Any) {
        when (value) {
            is List<*> -> {
                if (value.isNotEmpty()) {
                    val first = value[0]
                    when (first) {
                        is List<*>, is Map<*, *> ->
                            value.forEachIndexed { index, child ->
                                putFlat("$key.$index", child!!)
                            }
                        else -> {
                            if (value.map { it.toString() }.any { it.contains(',') }) {
                                value.forEachIndexed { index, child ->
                                    putFlat("$key.$index", child!!)
                                }
                            } else {
                                put(key, value.joinToString(","))
                            }
                        }
                    }
                } else {
                    put(key, "")
                }
            }
            is Map<*, *> ->
                value.forEach { (suffix, child) ->
                    putFlat("$key.$suffix", child!!)
                }
            else -> put(key, value.toString())
        }
    }
    return mutableMapOf<String, String>().apply {
        for ((key, value) in this@toFlatMap.toMap()) {
            putFlat(key, value)
        }
    }
}
