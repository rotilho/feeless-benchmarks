package dev.feeless.benchmarks.nano

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.Utils
import net.i2p.crypto.eddsa.math.Curve
import net.i2p.crypto.eddsa.math.Field
import net.i2p.crypto.eddsa.math.ed25519.Ed25519LittleEndianEncoding
import net.i2p.crypto.eddsa.math.ed25519.Ed25519ScalarOps
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

object NanoEd25519 {
    private const val BLAKE2B_512_LOOKUP = "BLAKE2B-512"
    private val provider = BouncyCastleProvider()

    init {
        if (Security.getProvider(provider.name) == null) Security.addProvider(provider)
    }

    private val curveSpec: EdDSANamedCurveSpec = createCurveSpec()

    fun publicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Nano private key must contain 32 bytes" }
        return EdDSAPrivateKeySpec(privateKey, curveSpec).a.toByteArray()
    }

    fun sign(
        privateKey: ByteArray,
        message: ByteArray,
    ): ByteArray {
        require(privateKey.size == 32) { "Nano private key must contain 32 bytes" }
        val engine = EdDSAEngine(blake2b512())
        engine.initSign(EdDSAPrivateKey(EdDSAPrivateKeySpec(privateKey, curveSpec)))
        engine.setParameter(EdDSAEngine.ONE_SHOT_MODE)
        engine.update(message)
        return engine.sign()
    }

    fun verify(
        publicKey: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        return runCatching {
            val engine = EdDSAEngine(blake2b512())
            engine.initVerify(EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, curveSpec)))
            engine.setParameter(EdDSAEngine.ONE_SHOT_MODE)
            engine.update(message)
            engine.verify(signature)
        }.getOrDefault(false)
    }

    private fun blake2b512(): MessageDigest = Blake2b512MessageDigest()

    private fun createCurveSpec(): EdDSANamedCurveSpec {
        val field =
            Field(
                256,
                Utils.hexToBytes("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
                Ed25519LittleEndianEncoding(),
            )
        val curve =
            Curve(
                field,
                Utils.hexToBytes("a3785913ca4deb75abd841414d0a700098e879777940c78c73fe6f2bee6c0352"),
                field.fromByteArray(Utils.hexToBytes("b0a00e4a271beec478e42fad0618432fa7d7fb3d99004d2b0bdfc14f8024832b")),
            )
        return EdDSANamedCurveSpec(
            EdDSANamedCurveTable.ED_25519,
            curve,
            BLAKE2B_512_LOOKUP,
            Ed25519ScalarOps(),
            curve.createPoint(
                Utils.hexToBytes("5866666666666666666666666666666666666666666666666666666666666666"),
                true,
            ),
        )
    }

    private class Blake2b512MessageDigest : MessageDigest(BLAKE2B_512_LOOKUP) {
        private val digest = Blake2bDigest(512)

        override fun engineUpdate(input: Byte) = digest.update(input)

        override fun engineUpdate(
            input: ByteArray,
            offset: Int,
            len: Int,
        ) = digest.update(input, offset, len)

        override fun engineDigest(): ByteArray = ByteArray(64).also { digest.doFinal(it, 0) }

        override fun engineReset() = digest.reset()

        override fun engineGetDigestLength(): Int = 64
    }
}
