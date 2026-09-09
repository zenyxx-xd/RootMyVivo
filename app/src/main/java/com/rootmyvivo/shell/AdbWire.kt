package com.rootmyvivo.shell

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.zip.CRC32
import javax.crypto.Cipher

/**
 * ADB wire-протокол клиент (подход LADB): прямое подключение к adbd на 127.0.0.1:5555.
 *
 * Проверено на реальном устройстве (PD2520, Android 15):
 *  - CNXN → AUTH TOKEN → AUTH SIGNATURE: RSA PKCS#1 v1.5 с SHA1 DigestInfo поверх
 *    СЫРОГО токена (prehashed-семантика, повторное хэширование не нужно).
 *  - Фолбэк: AUTH RSAPUBLICKEY (android-формат) → системный диалог авторизации.
 *  - Команды: OPEN "shell,v2,TERM=…,raw:CMD" + window-size; вывод фреймов
 *    [id][len LE32][payload], id: 1=stdout, 2=stderr, 3=exit.
 *  - Файлы: OPEN "exec:cat > PATH", сырые WRTE-чанки, CLSE = EOF.
 */
object AdbWire {

    private const val TAG = "NeoAdb"

    private const val CNXN = 0x4e584e43
    private const val AUTH = 0x48545541
    private const val OPEN = 0x4e45504f
    private const val OKAY = 0x59414b4f
    private const val CLSE = 0x45534c43
    private const val WRTE = 0x45545257

    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_RSAPUBLICKEY = 3

    private const val KEY_FILE = "adb_key.pkcs8.b64"
    private const val CHUNK = 4000
    private val TWO_32 = BigInteger.TWO.pow(32)

    class WireException(msg: String) : Exception(msg)

    private class Pkt(val cmd: Int, val a0: Int, val a1: Int, val data: ByteArray)

    // ─────────── RSA-ключи приложения ───────────

    @Volatile private var privateKey: PrivateKey? = null
    @Volatile private var publicKey: PublicKey? = null

    @Synchronized
    private fun ensureKeys(ctx: Context): Pair<PrivateKey, PublicKey> {
        privateKey?.let { priv -> publicKey?.let { pub -> return priv to pub } }
        val file = File(ctx.filesDir, KEY_FILE)
        val factory = KeyFactory.getInstance("RSA")
        val pair = if (file.exists()) {
            val priv = factory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(file.readText().trim(), Base64.DEFAULT))) as RSAPrivateCrtKey
            priv to factory.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent))
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            file.writeText(Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
            kp.private to kp.public
        }
        privateKey = pair.first
        publicKey = pair.second
        return pair
    }

    /** Публичный ключ в android-формате (base64, 524 байта) — для adb_keys. */
    fun publicKeyAndroid(ctx: Context): String? = try {
        val pub = ensureKeys(ctx).second as RSAPublicKey
        val n = pub.modulus
        val n0inv = n.modInverse(TWO_32).negate().mod(TWO_32)
        val rr = BigInteger.TWO.pow(4096).mod(n)
        val out = ByteArrayOutputStream(524)
        out.write(leInt(64))
        out.write(leBytes(n0inv, 4))
        out.write(leBytes(n, 256))
        out.write(leBytes(rr, 256))
        out.write(leBytes(pub.publicExponent, 4))
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "publicKeyAndroid failed", e)
        null
    }

    /** Подпись AUTH TOKEN: PKCS#1 v1.5 тип-1 поверх DigestInfo(SHA1)||token. */
    private fun signToken(priv: PrivateKey, token: ByteArray): ByteArray? = try {
        val digestInfo = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14,
        ) + token
        val keySize = 256
        val em = ByteArray(keySize)
        em[1] = 0x01
        val ps = keySize - 3 - digestInfo.size
        for (i in 0 until ps) em[2 + i] = 0xFF.toByte()
        System.arraycopy(digestInfo, 0, em, 2 + ps + 1, digestInfo.size)
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, priv)
        cipher.doFinal(em)
    } catch (e: Exception) {
        Log.e(TAG, "signToken failed", e)
        null
    }

    // ─────────── Соединение ───────────

    private var socket: Socket? = null
    private var rbuf = ByteArray(0)
    private var localId = 0

    /**
     * Подключиться и пройти авторизацию.
     * allowDialog=true — при неизвестном ключе отправить RSAPUBLICKEY и ждать диалог
     * (authTimeoutMs); false — быстро отказаться (smoke-тесты без диалога на экране).
     */
    @Synchronized
    fun connect(
        ctx: Context,
        port: Int = 5555,
        allowDialog: Boolean = false,
        authTimeoutMs: Int = 30_000,
    ) {
        socket?.let { s -> if (s.isConnected && !s.isClosed) return }
        close()

        val (priv, pub) = ensureKeys(ctx)
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress("127.0.0.1", port), 5000)
        s.soTimeout = 10_000
        socket = s
        rbuf = ByteArray(0)
        localId = 0

        send(CNXN, 0x01000001, 0x100000, "host::features=shell_v2,cmd".toByteArray())

        var p = recv(10_000) ?: fail("adbd closed connection")
        if (p.cmd == AUTH) {
            if (p.a0 != AUTH_TOKEN) fail("unexpected AUTH type ${p.a0}")
            val sig = signToken(priv, p.data) ?: fail("token signing failed")
            send(AUTH, AUTH_SIGNATURE, 0, sig)
            p = recv(10_000) ?: fail("adbd did not answer to signature")
            if (p.cmd == AUTH) {
                if (!allowDialog) fail("adb key not authorized")
                val key = publicKeyAndroid(ctx) ?: fail("no public key")
                send(AUTH, AUTH_RSAPUBLICKEY, 0, key.toByteArray(Charsets.US_ASCII) + byteArrayOf(0x0A))
                p = recv(authTimeoutMs)
                    ?: fail("not authorized: accept the dialog on screen")
            }
        }
        if (p.cmd != CNXN) fail("no CNXN after auth (0x${p.cmd.toString(16)})")
    }

    fun isConnected(): Boolean = socket?.let { it.isConnected && !it.isClosed } == true

    /** Выполнить команду в shell-домене: (exitCode, stdout+stderr). */
    @Synchronized
    fun shell(
        ctx: Context,
        command: String,
        timeoutMs: Long = 300_000,
        allowDialog: Boolean = false,
    ): Pair<Int, String> {
        connect(ctx, allowDialog = allowDialog)

        localId += 1
        val local = localId
        send(OPEN, local, 0, "shell,v2,TERM=xterm-256color,raw:$command\u0000".toByteArray())

        var remote = -1
        while (remote < 0) {
            val p = recv(10_000) ?: throw WireException("no OKAY for OPEN")
            when (p.cmd) {
                OKAY -> if (p.a1 == local || p.a0 == local) remote = if (p.a1 == local) p.a0 else p.a1
                CLSE -> if (p.a1 == local || p.a0 == local) throw WireException("stream closed immediately")
                else -> send(CLSE, p.a1, p.a0)
            }
        }

        send(WRTE, local, remote, byteArrayOf(4, 0, 0, 0, 0)) // window-size

        val acc = ByteArrayOutputStream()
        while (true) {
            val p = recv(timeoutMs.toInt()) ?: break
            when (p.cmd) {
                WRTE -> {
                    acc.write(p.data)
                    send(OKAY, local, remote)
                }
                CLSE -> {
                    if (p.a0 == remote || p.a1 == local) {
                        send(CLSE, local, remote)
                        break
                    } else {
                        send(CLSE, p.a1, p.a0)
                    }
                }
            }
        }
        return parseV2(acc.toByteArray())
    }

    /** Передать файл: exec:cat > path, сырые чанки, CLSE = EOF. Проверяет размер. */
    @Synchronized
    fun deploy(ctx: Context, localPath: String, remotePath: String): Boolean {
        return try {
            connect(ctx)
            val data = File(localPath).readBytes()

            localId += 1
            val local = localId
            send(OPEN, local, 0, "exec:cat > $remotePath\u0000".toByteArray())
            var remote = -1
            while (remote < 0) {
                val p = recv(10_000) ?: return false
                when (p.cmd) {
                    OKAY -> if (p.a1 == local || p.a0 == local) remote = if (p.a1 == local) p.a0 else p.a1
                    CLSE -> if (p.a1 == local || p.a0 == local) return false else send(CLSE, p.a1, p.a0)
                }
            }

            var off = 0
            while (off < data.size) {
                val len = minOf(CHUNK, data.size - off)
                send(WRTE, local, remote, data.copyOfRange(off, off + len))
                off += len
                while (true) {
                    val p = recv(10_000) ?: return false
                    if (p.cmd == OKAY) break
                    if (p.cmd == WRTE) send(OKAY, local, remote)
                    if (p.cmd == CLSE) return false
                }
            }
            send(CLSE, local, remote) // EOF
            while (true) {
                val p = recv(30_000) ?: break
                if (p.cmd == WRTE) send(OKAY, local, remote)
                if (p.cmd == CLSE) break
            }

            val (code, out) = shell(ctx, "wc -c < $remotePath")
            code == 0 && out.trim() == data.size.toString()
        } catch (e: Exception) {
            Log.e(TAG, "deploy failed", e)
            close()
            false
        }
    }

    @Synchronized
    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        rbuf = ByteArray(0)
    }

    // ─────────── Внутреннее ───────────

    private fun parseV2(stream: ByteArray): Pair<Int, String> {
        val sb = StringBuilder()
        var exit = -1
        var i = 0
        while (i + 5 <= stream.size) {
            val id = stream[i].toInt() and 0xFF
            val len = leUInt(stream, i + 1)
            if (len < 0 || i + 5 + len > stream.size) break
            val payload = stream.copyOfRange(i + 5, i + 5 + len)
            when (id) {
                1, 2 -> sb.append(String(payload, Charsets.UTF_8))
                3 -> if (len >= 1) exit = payload[0].toInt() and 0xFF
            }
            i += 5 + len
        }
        return (if (exit >= 0) exit else 0) to sb.toString()
    }

    private fun send(cmd: Int, a0: Int, a1: Int, data: ByteArray = ByteArray(0)) {
        val s = socket ?: throw WireException("no connection")
        val out = s.getOutputStream()
        out.write(packet(cmd, a0, a1, data))
        out.flush()
    }

    private fun recv(timeoutMs: Int): Pkt? {
        val s = socket ?: return null
        val inp = s.getInputStream()
        s.soTimeout = timeoutMs
        while (rbuf.size < 24) {
            val chunk = read(inp) ?: return null
            rbuf += chunk
        }
        val len = leUInt(rbuf, 12)
        if (len < 0 || len > 1_100_000) {
            close()
            return null
        }
        while (rbuf.size < 24 + len) {
            val chunk = read(inp) ?: return null
            rbuf += chunk
        }
        val pkt = Pkt(leUInt(rbuf, 0), leUInt(rbuf, 4), leUInt(rbuf, 8), rbuf.copyOfRange(24, 24 + len))
        rbuf = rbuf.copyOfRange(24 + len, rbuf.size)
        return pkt
    }

    private fun read(inp: java.io.InputStream): ByteArray? = try {
        val buf = ByteArray(65536)
        val n = inp.read(buf)
        if (n < 0) { close(); null } else buf.copyOf(n)
    } catch (e: SocketTimeoutException) {
        null
    } catch (e: Exception) {
        close()
        null
    }

    private fun packet(cmd: Int, a0: Int, a1: Int, data: ByteArray): ByteArray {
        val hdr = ByteArray(24)
        putLe(hdr, 0, cmd)
        putLe(hdr, 4, a0)
        putLe(hdr, 8, a1)
        putLe(hdr, 12, data.size)
        putLe(hdr, 16, CRC32().apply { update(data) }.value.toInt())
        putLe(hdr, 20, cmd.inv())
        val out = ByteArrayOutputStream(24 + data.size)
        out.write(hdr)
        out.write(data)
        return out.toByteArray()
    }

    private fun putLe(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun leUInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun leInt(v: Int): ByteArray = ByteArray(4).also { putLe(it, 0, v) }

    private fun leBytes(v: BigInteger, size: Int): ByteArray {
        val be = v.toByteArray()
        val trimmed = if (be.size > size) be.copyOfRange(be.size - size, be.size) else be
        val out = ByteArray(size)
        System.arraycopy(trimmed, 0, out, size - trimmed.size, trimmed.size)
        return out.reversedArray()
    }

    private fun fail(msg: String): Nothing {
        close()
        throw WireException(msg)
    }
}
