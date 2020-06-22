package com.example.blertc

import com.google.gson.*
import org.webrtc.IceCandidate
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

class SessionDescriptionSerializer : JsonSerializer<SessionDescription> {
    override fun serialize(
        src: SessionDescription,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement? {
        val jsonObject = JsonObject()
        jsonObject.addProperty("sdp", src.description)
        jsonObject.addProperty("type", src.type.canonicalForm())
        return jsonObject
    }
}

class ICEDeserializer : JsonDeserializer<IceCandidate> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement, typeOfT: Type?,
        context: JsonDeserializationContext?
    ): IceCandidate {
        val jsonObject = json.asJsonObject
        val sdp = jsonObject["candidate"].asString
        val sdpMid = jsonObject["sdpMid"].asString
        val sdpMLineIndex = jsonObject["sdpMLineIndex"].asInt
        return IceCandidate(sdpMid, sdpMLineIndex, sdp)
    }
}

class ICESerializer : JsonSerializer<IceCandidate> {
    override fun serialize(
        src: IceCandidate,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement? {
        val jsonObject = JsonObject()
        jsonObject.addProperty("candidate", src.sdp)
        return jsonObject
    }
}