package app.fen.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkVerifierTest {

    private val signers = setOf("aa", "bb")

    private fun info(
        pkg: String = "app.fen",
        code: Long = 19L,
        signerSet: Set<String> = signers,
    ) = ApkInspection(pkg, code, signerSet)

    @Test
    fun `matching package version and signer passes`() {
        assertEquals(ApkCheck.Ok, compareArchive(info(), "app.fen", 18L, signers))
    }

    @Test
    fun `equal version code is allowed`() {
        assertEquals(ApkCheck.Ok, compareArchive(info(code = 18L), "app.fen", 18L, signers))
    }

    @Test
    fun `lower version code is rejected`() {
        assertEquals(ApkCheck.TooOld(17L), compareArchive(info(code = 17L), "app.fen", 18L, signers))
    }

    @Test
    fun `other package is rejected`() {
        assertEquals(
            ApkCheck.WrongPackage,
            compareArchive(info(pkg = "app.fen.dev.feat_update"), "app.fen", 18L, signers),
        )
    }

    @Test
    fun `different signing certificate is rejected`() {
        assertEquals(ApkCheck.WrongSignature, compareArchive(info(signerSet = setOf("cc")), "app.fen", 18L, signers))
    }

    @Test
    fun `unreadable archive is reported`() {
        assertEquals(ApkCheck.Unreadable, compareArchive(null, "app.fen", 18L, signers))
    }

    @Test
    fun `sha256 text handles checksum file formats`() {
        val hash = "a".repeat(64)
        assertEquals(hash, parseSha256Text("$hash  Fen-1.1.0.apk"))
        assertEquals(hash, parseSha256Text("${hash.uppercase()}\n"))
        assertEquals(hash, parseSha256Text(hash))
    }

    @Test
    fun `sha256 text rejects junk`() {
        assertEquals(null, parseSha256Text(""))
        assertEquals(null, parseSha256Text("not a checksum"))
    }

    @Test
    fun `sha256Hex matches known vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(byteArrayOf()),
        )
    }
}
