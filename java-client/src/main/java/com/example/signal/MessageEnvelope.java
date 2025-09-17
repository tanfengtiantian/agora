package com.example.signal;

import org.whispersystems.libsignal.protocol.CiphertextMessage;

import java.util.Base64;

/**
 * Wrapper for encrypted messages exchanged between the simulated clients.
 */
public class MessageEnvelope {
    public static final String TYPE_PREKEY = "PREKEY";
    public static final String TYPE_SIGNAL = "SIGNAL";

    private String type;
    private String body;

    public MessageEnvelope() {
        // For JSON deserialisation
    }

    public MessageEnvelope(String type, String body) {
        this.type = type;
        this.body = body;
    }

    public static MessageEnvelope fromCiphertext(CiphertextMessage message) {
        String type = message.getType() == CiphertextMessage.PREKEY_TYPE ? TYPE_PREKEY : TYPE_SIGNAL;
        String body = Base64.getEncoder().encodeToString(message.serialize());
        return new MessageEnvelope(type, body);
    }

    public String getType() {
        return type;
    }

    public String getBody() {
        return body;
    }
}
