package dev.feeless.benchmarks.nano

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NanoCryptoTest {
    @Test
    fun `dev genesis account decodes to its canonical public key`() {
        // Given
        val account = NanoFixtures.DEV_GENESIS_ACCOUNT

        // When
        val publicKey = NanoAccounts.decode(account)

        // Then
        assertEquals(NanoFixtures.DEV_GENESIS_PUBLIC_KEY, publicKey.upperHex())
        assertEquals(account, NanoAccounts.encode(publicKey))
        assertTrue(NanoAccounts.isValid(account))
    }

    @Test
    fun `dev genesis private key derives its canonical public key`() {
        // Given
        val privateKey = NanoFixtures.DEV_GENESIS_PRIVATE_KEY.hexBytes(32)

        // When
        val publicKey = NanoEd25519.publicKey(privateKey)

        // Then
        assertEquals(NanoFixtures.DEV_GENESIS_PUBLIC_KEY, publicKey.upperHex())
    }

    @Test
    fun `state block hash matches the JNano golden vector`() {
        // Given
        val account = "xrb_3igf8hd4sjshoibbbkeitmgkp1o6ug4xads43j6e4gqkj5xk5o83j8ja9php"
        val representative = "xrb_3p1asma84n8k84joneka776q4egm5wwru3suho9wjsfyuem8j95b3c78nw8j"

        // When
        val hash =
            NanoStateBlock.hash(
                account = account,
                previous = "0",
                representative = representative,
                balance = "1",
                link = "1EF0AD02257987B48030CC8D38511D3B2511672F33AF115AD09E18A86A8355A8",
            )

        // Then
        assertEquals("FC5A7FB777110A858052468D448B2DF22B648943C097C0608D1E2341007438B0", hash)
    }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun `existing and newly generated Ed25519-Blake2b signatures match JNano vectors`() {
        // Given
        val privateKey = "9F0E444C69F77A49BD0BE89DB92C38FE713E0963165CCA12FAF5712D7657120F".hexBytes(32)
        val publicKey = NanoEd25519.publicKey(privateKey)
        val message = "AEC75F807DCE45AFA787DE7B395BE498A885525569DD614162E0C80FD4F27EE9".hexBytes(32)
        val expected = "1123C926EF53B0FFA3585D5F6FA17D05B2AAD486D28CBEED88837B83265F264CBAF3FEA78AF80AAB4C59740546B220ADBE207F6B800FFE864E0934E9C1078401"

        // When
        val generated = NanoEd25519.sign(privateKey, message)

        // Then
        assertEquals(expected, generated.upperHex())
        assertTrue(NanoEd25519.verify(publicKey, message, expected.hexBytes(64)))
        assertFalse(NanoEd25519.verify(publicKey, message, expected.drop(2).padEnd(128, '0').hexBytes(64)))
    }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun `existing Nano benchmark signature verifies against its predicted state hash`() {
        // Given
        val block =
            NanoStateBlock(
                account = NanoFixtures.DEV_GENESIS_ACCOUNT,
                balance = "340282366920938463463374607431768211454",
                link = "D69FF9998E5D0E23C8D6B177345CF60E0C70030ACFF86F2ED40BF698E827632B",
                linkAsAccount = "nano_3onzz8erwqag6h6ffedq8jghe5ieg13iomzrfwqfa4zpm5n4grsdjmeqizkk",
                previous = NanoFixtures.DEV_GENESIS_HASH,
                representative = NanoFixtures.DEV_GENESIS_ACCOUNT,
                signature = "8CA267DF9D113BF770FCF194DAC3C00528ACFAA4B43E81F1874A781B34D062FE943B2AFB12B20FE8457F08EB3590DCC49E5E3282EFA23C12A4056142AF9CE40C",
                work = "cc77fe42d453ccc7",
            )

        // When
        val hash = block.hash()

        // Then
        assertEquals("04DCB6289E447A6496C188E2D6E336A9D790C199342C95920BF7D24418F01D4C", hash)
        assertTrue(block.hasValidSignature())
    }

    @Test
    fun `work difficulty uses unsigned little-endian comparison`() {
        // Given
        val root = "D1E6C3C6B7DF4485B9324AB4DE023B71B5E0CA9AC20616A6F6E80D15AD4CFAC6"
        val valid = "8e8206e47e15b74b"
        val invalid = "be46b2e52b34f535"

        // When
        val validDifficulty = NanoWork.difficulty(root, valid)
        val invalidDifficulty = NanoWork.difficulty(root, invalid)

        // Then
        assertTrue(validDifficulty >= NanoWork.EPOCH_2_SEND_CHANGE.toULong(16))
        assertTrue(invalidDifficulty < NanoWork.EPOCH_2_SEND_CHANGE.toULong(16))
        assertTrue(NanoWork.isValid(root, valid, NanoLedgerEpoch.EPOCH_2, NanoBlockSubtype.SEND))
        assertFalse(NanoWork.isValid(root, invalid, NanoLedgerEpoch.EPOCH_2, NanoBlockSubtype.SEND))
    }

    @Test
    fun `deterministic work generation is repeatable at the epoch zero threshold`() {
        // Given
        val root = NanoFixtures.DEV_GENESIS_HASH

        // When
        val first = NanoWork.deterministic(root, NanoLedgerEpoch.EPOCH_0, NanoBlockSubtype.SEND)
        val second = NanoWork.deterministic(root, NanoLedgerEpoch.EPOCH_0, NanoBlockSubtype.SEND)

        // Then
        assertEquals(first, second)
        assertTrue(NanoWork.isValid(root, first, NanoLedgerEpoch.EPOCH_0, NanoBlockSubtype.SEND))
    }

    @Test
    fun `threshold profile distinguishes epoch and subtype`() {
        // Given
        val cases =
            listOf(
                Triple(NanoLedgerEpoch.EPOCH_0, NanoBlockSubtype.SEND, NanoWork.EPOCH_0_AND_1),
                Triple(NanoLedgerEpoch.EPOCH_1, NanoBlockSubtype.RECEIVE, NanoWork.EPOCH_0_AND_1),
                Triple(NanoLedgerEpoch.EPOCH_2, NanoBlockSubtype.CHANGE, NanoWork.EPOCH_2_SEND_CHANGE),
                Triple(NanoLedgerEpoch.EPOCH_2, NanoBlockSubtype.RECEIVE, NanoWork.EPOCH_2_RECEIVE_EPOCH),
            )

        // When
        val thresholds = cases.map { (epoch, subtype) -> NanoWork.threshold(epoch, subtype) }

        // Then
        assertContentEquals(cases.map { it.third.toULong(16) }, thresholds)
    }
}
