package com.tvapp.livetv.data

object ChannelOrderer {
    fun moveToPosition(
        order: List<String>,
        selectedKeys: Set<String>,
        startNumber: Int,
    ): List<String> {
        if (selectedKeys.isEmpty()) return order
        val selected = order.filter { it in selectedKeys }
        if (selected.isEmpty()) return order
        val remaining = order.filterNot { it in selectedKeys }.toMutableList()
        val insertionIndex = (startNumber - 1).coerceIn(0, remaining.size)
        remaining.addAll(insertionIndex, selected)
        return remaining
    }
}
