package com.example.blertc

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import org.webrtc.SessionDescription
import java.lang.reflect.Type

class SessionDescriptionDeserializer : JsonDeserializer<SessionDescription> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement, typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SessionDescription {
        val jsonObject = json.asJsonObject
        val type = jsonObject["type"].asString
        val sdp = jsonObject["sdp"].asString

        return SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
    }
}