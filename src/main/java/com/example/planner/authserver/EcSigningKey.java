package com.example.planner.authserver;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Ключ подписи токенов ES256 из строки настроек (закрытый ключ P-256, PKCS#8, base64).
 *
 * <p>Открытый ключ в настройках не храним — он однозначно вычисляется из закрытого:
 * одна переменная окружения вместо двух, и они не могут «разойтись».
 */
final class EcSigningKey {

    private EcSigningKey() {
    }

    /**
     * @return ключ в формате JWK (как его понимает Spring Authorization Server). Идентификатор ключа (kid) —
     *         отпечаток открытого ключа (RFC 7638): одинаковый после каждого перезапуска и на всех копиях приложения.
     */
    static ECKey parse(String base64Pkcs8) {
        ECPrivateKey privateKey = readPrivateKey(base64Pkcs8);
        if (!Curve.P_256.equals(Curve.forECParameterSpec(privateKey.getParams()))) {
            throw new IllegalStateException("Ключ подписи должен быть на кривой P-256 (ES256)");
        }
        try {
            return new ECKey.Builder(Curve.P_256, derivePublicKey(privateKey))
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Не удалось собрать ключ подписи", e);
        }
    }

    private static ECPrivateKey readPrivateKey(String base64Pkcs8) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Pkcs8.strip());
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY — не закрытый EC-ключ в формате PKCS#8/base64. Сгенерировать: "
                            + "openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256"
                            + " | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0", e);
        }
    }

    /** Открытый ключ = закрытое число × базовая точка кривой. Арифметику кривой делает BouncyCastle. */
    private static ECPublicKey derivePublicKey(ECPrivateKey privateKey) {
        try {
            ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp256r1");
            org.bouncycastle.math.ec.ECPoint q = curve.getG().multiply(privateKey.getS()).normalize();
            ECPoint publicPoint = new ECPoint(q.getAffineXCoord().toBigInteger(), q.getAffineYCoord().toBigInteger());
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(publicPoint, privateKey.getParams()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось вычислить открытый ключ", e);
        }
    }
}
