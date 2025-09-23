package com.example.signal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A simple demonstration of two participants (Alice and Bob) establishing a shared
 * symmetric session key and exchanging encrypted messages using AES/GCM.
 */
public final class SignalDemo {

    private SignalDemo() {
    }

    public static void main(String[] args) throws Exception {
        Participant alice = Participant.create("Alice");
        Participant bob = Participant.create("Bob");

        // Exchange pre-key bundles so both sides derive the same session key.
        alice.establishSession(bob.asPreKeyBundle());
        bob.establishSession(alice.asPreKeyBundle());

        String alicePlaintext = "Hi Bob! This is Alice.";
        EncryptedMessage firstMessage = alice.encrypt(alicePlaintext);
        System.out.println("Alice encrypted: " + firstMessage.asBase64());
        System.out.println("Bob decrypted: " + bob.decrypt(firstMessage));

        String bobPlaintext = "Nice to hear from you, Alice!";
        EncryptedMessage secondMessage = bob.encrypt(bobPlaintext);
        System.out.println("Bob encrypted: " + secondMessage.asBase64());
        System.out.println("Alice decrypted: " + alice.decrypt(secondMessage));
    }

    private static final class Participant {
        private static final SecureRandom RANDOM = new SecureRandom();

        private final String name;
        private final byte[] localKeyMaterial;
        private SecretKey sessionKey;

        private Participant(String name, byte[] localKeyMaterial) {
            this.name = name;
            this.localKeyMaterial = localKeyMaterial;
        }

        static Participant create(String name) throws Exception {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(128);
            SecretKey secretKey = keyGenerator.generateKey();
            return new Participant(name, secretKey.getEncoded());
        }

        PreKeyBundle asPreKeyBundle() {
            return new PreKeyBundle(Arrays.copyOf(localKeyMaterial, localKeyMaterial.length));
        }

        void establishSession(PreKeyBundle remoteBundle) throws Exception {
            this.sessionKey = new SecretKeySpec(deriveSharedSecret(remoteBundle), "AES");
        }

        EncryptedMessage encrypt(String plaintext) throws Exception {
            ensureSessionEstablished();
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedMessage(iv, ciphertext);
        }

        String decrypt(EncryptedMessage message) throws Exception {
            ensureSessionEstablished();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, new GCMParameterSpec(128, message.iv));
            byte[] plaintext = cipher.doFinal(message.ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        }

        private byte[] deriveSharedSecret(PreKeyBundle remoteBundle) throws Exception {
            byte[] remoteKeyMaterial = remoteBundle.keyMaterial;

            byte[] first = localKeyMaterial;
            byte[] second = remoteKeyMaterial;
            if (compare(first, second) > 0) {
                byte[] tmp = first;
                first = second;
                second = tmp;
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(first);
            digest.update(second);
            byte[] fullKey = digest.digest();
            return Arrays.copyOf(fullKey, 16);
        }

        private int compare(byte[] first, byte[] second) {
            int min = Math.min(first.length, second.length);
            for (int i = 0; i < min; i++) {
                int diff = (first[i] & 0xFF) - (second[i] & 0xFF);
                if (diff != 0) {
                    return diff;
                }
            }
            return first.length - second.length;
        }

        private void ensureSessionEstablished() {
            if (sessionKey == null) {
                throw new IllegalStateException(name + " has not established the session yet");
            }
        }
    }

    private static final class PreKeyBundle {
        private final byte[] keyMaterial;

        private PreKeyBundle(byte[] keyMaterial) {
            this.keyMaterial = keyMaterial;
        }
    }

    private static final class EncryptedMessage {
        private final byte[] iv;
        private final byte[] ciphertext;

        private EncryptedMessage(byte[] iv, byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }

        String asBase64() {
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        }
    }
}

