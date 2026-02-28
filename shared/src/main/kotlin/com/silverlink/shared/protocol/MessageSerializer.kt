package com.silverlink.shared.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MessageSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun serialize(message: WatchMessage): ByteArray {
        return json.encodeToString(message).toByteArray(Charsets.UTF_8)
    }

    fun deserialize(data: ByteArray): WatchMessage {
        return json.decodeFromString(data.toString(Charsets.UTF_8))
    }

    fun serializeToString(message: WatchMessage): String {
        return json.encodeToString(message)
    }

    fun deserializeFromString(data: String): WatchMessage {
        return json.decodeFromString(data)
    }
}
