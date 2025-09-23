package com.example.signal;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.signal.libsignal.protocol.CiphertextMessage;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.message.SignalMessage;
import org.signal.libsignal.protocol.state.InMemorySignalProtocolStore;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.KeyHelper;

public class SignalDemo {

    public static void main(String[] args) throws Exception {
        ConversationParticipant alice = ConversationParticipant.create("Alice", 1);
        ConversationParticipant bob = ConversationParticipant.create("Bob", 1);

        // Share Bob's pre-key bundle with Alice so she can establish a session.
        SessionBuilder aliceSessionBuilder = new SessionBuilder(alice.store, bob.address);
        aliceSessionBuilder.process(bob.asPreKeyBundle());

        SessionCipher aliceSessionCipher = new SessionCipher(alice.store, bob.address);
        SessionCipher bobSessionCipher = new SessionCipher(bob.store, alice.address);

        String alicePlaintext = "Hi Bob! This is Alice.";
        CiphertextMessage firstMessage = aliceSessionCipher.encrypt(alicePlaintext.getBytes(StandardCharsets.UTF_8));
        System.out.println("Alice encrypted: " + toHex(firstMessage.serialize()));

        PreKeySignalMessage incomingPreKeyMessage = new PreKeySignalMessage(firstMessage.serialize());
        byte[] decryptedByBob = bobSessionCipher.decrypt(incomingPreKeyMessage);
        System.out.println("Bob decrypted: " + new String(decryptedByBob, StandardCharsets.UTF_8));

        String bobPlaintext = "Nice to hear from you, Alice!";
        CiphertextMessage secondMessage = bobSessionCipher.encrypt(bobPlaintext.getBytes(StandardCharsets.UTF_8));
        System.out.println("Bob encrypted: " + toHex(secondMessage.serialize()));

        SignalMessage incomingSignalMessage = new SignalMessage(secondMessage.serialize());
        byte[] decryptedByAlice = aliceSessionCipher.decrypt(incomingSignalMessage);
        System.out.println("Alice decrypted: " + new String(decryptedByAlice, StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class ConversationParticipant {
        private final SignalProtocolAddress address;
        private final InMemorySignalProtocolStore store;
        private final PreKeyRecord preKey;
        private final SignedPreKeyRecord signedPreKey;

        private ConversationParticipant(String name, int deviceId, IdentityKeyPair identityKeyPair,
                int registrationId, PreKeyRecord preKey, SignedPreKeyRecord signedPreKey) {
            this.address = new SignalProtocolAddress(name, deviceId);
            this.store = new InMemorySignalProtocolStore(identityKeyPair, registrationId);
            this.preKey = preKey;
            this.signedPreKey = signedPreKey;

            this.store.storePreKey(preKey.getId(), preKey);
            this.store.storeSignedPreKey(signedPreKey.getId(), signedPreKey);
        }

        private static ConversationParticipant create(String name, int deviceId) throws Exception {
            IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
            int registrationId = KeyHelper.generateRegistrationId(false);
            List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(1, 1);
            PreKeyRecord preKey = preKeys.get(0);
            SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 1);
            return new ConversationParticipant(name, deviceId, identityKeyPair, registrationId, preKey, signedPreKey);
        }

        private PreKeyBundle asPreKeyBundle() {
            ECPublicKey preKeyPublic = preKey.getKeyPair().getPublicKey();
            ECPublicKey signedPreKeyPublic = signedPreKey.getKeyPair().getPublicKey();
            return new PreKeyBundle(
                    store.getLocalRegistrationId(),
                    address.getDeviceId(),
                    preKey.getId(),
                    preKeyPublic,
                    signedPreKey.getId(),
                    signedPreKeyPublic,
                    signedPreKey.getSignature(),
                    store.getIdentityKeyPair().getPublicKey());
        }
    }
}
