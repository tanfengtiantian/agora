package com.example.signal;

import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.ratchet.SessionBuilder;
import org.whispersystems.libsignal.ratchet.SessionCipher;
import org.whispersystems.libsignal.state.InMemorySignalProtocolStore;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple wrapper around libsignal state needed for this sample.
 */
public class SignalClient {
    private final String name;
    private final SignalProtocolAddress address;
    private final IdentityKeyPair identityKeyPair;
    private final int registrationId;
    private final InMemorySignalProtocolStore store;
    private final AtomicInteger preKeyIdCounter = new AtomicInteger(1);
    private final AtomicInteger signedPreKeyIdCounter = new AtomicInteger(1);

    private PreKeyRecord currentPreKey;
    private SignedPreKeyRecord currentSignedPreKey;

    public SignalClient(String name) {
        try {
            this.name = name;
            this.address = new SignalProtocolAddress(name, 1);
            this.identityKeyPair = KeyHelper.generateIdentityKeyPair();
            this.registrationId = KeyHelper.generateRegistrationId(false);
            this.store = new InMemorySignalProtocolStore(identityKeyPair, registrationId);
            refreshPreKeys();
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Unable to initialise Signal client", e);
        }
    }

    public String getName() {
        return name;
    }

    public SignalProtocolAddress getAddress() {
        return address;
    }

    public PreKeyBundleDTO generatePreKeyBundle() {
        try {
            refreshPreKeys();
            return PreKeyBundleDTO.from(identityKeyPair, registrationId, address.getDeviceId(), currentPreKey, currentSignedPreKey);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Failed to create pre-key bundle", e);
        }
    }

    public void processPreKeyBundle(String remoteName, PreKeyBundleDTO bundleDTO) {
        try {
            SessionBuilder sessionBuilder = new SessionBuilder(store, new SignalProtocolAddress(remoteName, 1));
            sessionBuilder.process(bundleDTO.toPreKeyBundle());
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid pre-key bundle received", e);
        }
    }

    public MessageEnvelope encrypt(String remoteName, String plaintext) {
        try {
            SessionCipher cipher = new SessionCipher(store, new SignalProtocolAddress(remoteName, 1));
            CiphertextMessage message = cipher.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
            return MessageEnvelope.fromCiphertext(message);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt message", e);
        }
    }

    public String decrypt(String remoteName, MessageEnvelope envelope) {
        try {
            SessionCipher cipher = new SessionCipher(store, new SignalProtocolAddress(remoteName, 1));
            byte[] body = Base64.getDecoder().decode(envelope.getBody());
            byte[] plaintext;
            if (MessageEnvelope.TYPE_PREKEY.equals(envelope.getType())) {
                plaintext = cipher.decrypt(new PreKeySignalMessage(body));
            } else {
                plaintext = cipher.decrypt(new SignalMessage(body));
            }
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt message", e);
        }
    }

    private void refreshPreKeys() throws InvalidKeyException {
        this.currentPreKey = KeyHelper.generatePreKey(preKeyIdCounter.getAndIncrement());
        this.currentSignedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyIdCounter.getAndIncrement());
        store.storePreKey(currentPreKey.getId(), currentPreKey);
        store.storeSignedPreKey(currentSignedPreKey.getId(), currentSignedPreKey);
    }
}
