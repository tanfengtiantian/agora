package com.example.signal;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.util.Base64;

/**
 * DTO used to serialize and deserialize pre-key bundles between clients.
 */
public class PreKeyBundleDTO {
    private int registrationId;
    private int deviceId;
    private int preKeyId;
    private String preKeyPublic;
    private int signedPreKeyId;
    private String signedPreKeyPublic;
    private String signedPreKeySignature;
    private String identityKeyPublic;

    public PreKeyBundleDTO() {
        // For JSON deserialisation
    }

    public PreKeyBundleDTO(int registrationId, int deviceId, int preKeyId, String preKeyPublic,
                           int signedPreKeyId, String signedPreKeyPublic, String signedPreKeySignature,
                           String identityKeyPublic) {
        this.registrationId = registrationId;
        this.deviceId = deviceId;
        this.preKeyId = preKeyId;
        this.preKeyPublic = preKeyPublic;
        this.signedPreKeyId = signedPreKeyId;
        this.signedPreKeyPublic = signedPreKeyPublic;
        this.signedPreKeySignature = signedPreKeySignature;
        this.identityKeyPublic = identityKeyPublic;
    }

    public static PreKeyBundleDTO from(IdentityKeyPair identityKeyPair, int registrationId, int deviceId,
                                       PreKeyRecord preKey, SignedPreKeyRecord signedPreKey) {
        ECKeyPair preKeyPair = preKey.getKeyPair();
        ECKeyPair signedPreKeyPair = signedPreKey.getKeyPair();
        return new PreKeyBundleDTO(
                registrationId,
                deviceId,
                preKey.getId(),
                Base64.getEncoder().encodeToString(preKeyPair.getPublicKey().serialize()),
                signedPreKey.getId(),
                Base64.getEncoder().encodeToString(signedPreKeyPair.getPublicKey().serialize()),
                Base64.getEncoder().encodeToString(signedPreKey.getSignature()),
                Base64.getEncoder().encodeToString(identityKeyPair.getPublicKey().serialize())
        );
    }

    public int getRegistrationId() {
        return registrationId;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public int getPreKeyId() {
        return preKeyId;
    }

    public String getPreKeyPublic() {
        return preKeyPublic;
    }

    public int getSignedPreKeyId() {
        return signedPreKeyId;
    }

    public String getSignedPreKeyPublic() {
        return signedPreKeyPublic;
    }

    public String getSignedPreKeySignature() {
        return signedPreKeySignature;
    }

    public String getIdentityKeyPublic() {
        return identityKeyPublic;
    }

    public PreKeyBundle toPreKeyBundle() throws InvalidKeyException {
        ECPublicKey preKeyPublicKey = Curve.decodePoint(Base64.getDecoder().decode(preKeyPublic), 0);
        ECPublicKey signedPreKeyPublicKey = Curve.decodePoint(Base64.getDecoder().decode(signedPreKeyPublic), 0);
        IdentityKey identityKey = new IdentityKey(Base64.getDecoder().decode(identityKeyPublic), 0);
        byte[] signature = Base64.getDecoder().decode(signedPreKeySignature);
        return new PreKeyBundle(
                registrationId,
                deviceId,
                preKeyId,
                preKeyPublicKey,
                signedPreKeyId,
                signedPreKeyPublicKey,
                signature,
                identityKey
        );
    }
}
