package com.example.planner.authserver;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcSigningKeyTest {

    @Test
    void derivedPublicKeyIsTheRealOne() throws Exception {
        KeyPair keyPair = generate("secp256r1");

        ECKey key = EcSigningKey.parse(base64Pkcs8(keyPair));

        assertThat(key.toECPublicKey().getW()).isEqualTo(((java.security.interfaces.ECPublicKey) keyPair.getPublic()).getW());
    }

    @Test
    void signatureMadeWithTheKeyVerifiesWithItsPublicPart() throws Exception {
        ECKey key = EcSigningKey.parse(base64Pkcs8(generate("secp256r1")));
        JWSObject token = new JWSObject(new JWSHeader(JWSAlgorithm.ES256), new Payload("{\"sub\":\"ivan\"}"));

        token.sign(new ECDSASigner(key));

        assertThat(token.verify(new ECDSAVerifier(key.toPublicJWK()))).isTrue();
    }

    /** kid — отпечаток открытого ключа: тот же ключ после перезапуска даёт тот же kid. */
    @Test
    void keyIdIsStableForTheSameKey() throws Exception {
        String sameKey = base64Pkcs8(generate("secp256r1"));

        assertThat(EcSigningKey.parse(sameKey).getKeyID()).isEqualTo(EcSigningKey.parse(sameKey).getKeyID());
    }

    @Test
    void keyOnAnotherCurveIsRejected() throws Exception {
        String p384 = base64Pkcs8(generate("secp384r1"));

        assertThatThrownBy(() -> EcSigningKey.parse(p384))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P-256");
    }

    @Test
    void garbageIsRejectedWithHowToGenerateHint() {
        assertThatThrownBy(() -> EcSigningKey.parse("not-a-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl genpkey");
    }

    private static KeyPair generate(String curve) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }

    private static String base64Pkcs8(KeyPair keyPair) {
        // Java кодирует закрытый ключ именно в PKCS#8 — как в JWT_SIGNING_KEY.
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }
}
