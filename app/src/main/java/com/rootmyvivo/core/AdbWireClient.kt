package com.rootmyvivo.core

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
 * Протокол проверен прототипом на реальном устройстве (PD2520, Android 15):
 *  - CNXN → AUTH TOKEN → AUTH SIGNATURE: RSA PKCS#1 v1.5 с SHA1 DigestInfo
 *    поверх СЫРОГО токена (токен уже считается дайджестом, повторное хэширование не нужно).
 *  - Фолбэк при отказе подписи: AUTH RSAPUBLICKEY (android-формат, 524 байта) —
 *    на экране появляется системный диалог «Разрешить отладку по USB?».
 *  - Команды: OPEN "shell,v2,TERM=xterm-256color,raw:CMD" + window-size пакет;
 *    вывод фреймирован: [id][len LE32][payload], id: 1=stdout, 2=stderr, 3=exit.
 *  - Передача файлов: OPEN "exec:cat > PATH", сырые WRTE-чанки, CLSE = EOF.
 */
object AdbWireClient {

    private const val TAG = "RootMyVivo"

    private const val CNXN = 0x4e584e43
    private const val AUTH = 0x48545541
    private const val OPEN = 0x4e45504f
    private const val OKAY = 0x59414b4f
    private const val CLSE = 0x45534c43
    private const val WRTE = 0x45545257

    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_RSAPUBLICKEY = 3

    private const val KEY_PRIV_FILE = "adb_key.pkcs8.b64"
    private const val MAX_PAYLOAD = 4000
    private val TWO_32 = BigInteger.TWO.pow(32)

    class WireException(msg: String) : Exception(msg)

    private class Pkt(val cmd: Int, val a0: Int, val a1: Int, val data: ByteArray)

    // ─────────── Ключи ───────────

    @Volatile private var privateKey: PrivateKey? = null
    @Volatile private var publicKey: PublicKey? = null

    @Synchronized
    fun ensureKeys(ctx: Context): Pair<PrivateKey, PublicKey>? {
        privateKey?.let { priv -> publicKey?.let { pub -> return priv to pub } }
        return try {
            val privFile = File(ctx.filesDir, KEY_PRIV_FILE)
            val factory = KeyFactory.getInstance("RSA")
            if (privFile.exists()) {
                val pkcs8 = Base64.decode(privFile.readText().trim(), Base64.DEFAULT)
                val priv = factory.generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateCrtKey
                val pub = factory.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent))
                privateKey = priv; publicKey = pub
                priv to pub
            } else {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(2048)
                val kp = kpg.generateKeyPair()
                privFile.writeText(Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
                privateKey = kp.private; publicKey = kp.public
                kp.private to kp.public
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureKeys failed", e)
            null
        }
    }

    /** Публичный ключ в android-формате (base64) — для RSAPUBLICKEY и /data/misc/adb/adb_keys. */
    fun androidPubkey(ctx: Context): String? {
        val (_, pub) = ensureKeys(ctx) ?: return null
        return try {
            val rsa = pub as RSAPublicKey
            val n = rsa.modulus
            val e = rsa.publicExponent
            val n0inv = n.modInverse(TWO_32).negate().mod(TWO_32)
            val rr = BigInteger.TWO.pow(4096).mod(n)
            val out = ByteArrayOutputStream(524)
            writeLeInt(out, 64)              // размер модуля в 32-битных словах
            out.write(bigIntLe(n0inv, 4))    // -1/n mod 2^32
            out.write(bigIntLe(n, 256))      // модуль (little-endian)
            out.write(bigIntLe(rr, 256))     // R^2 mod n (Montgomery)
            out.write(bigIntLe(e, 4))        // экспонента
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "androidPubkey failed", e)
            null
        }
    }

    /**
     * Подпись AUTH TOKEN: PKCS#1 v1.5 тип-1 поверх DigestInfo(SHA1)||token.
     * Токен НЕ хэшируется повторно — он уже «дайджест» (семантика prehashed).
     */
    private fun signToken(priv: PrivateKey, token: ByteArray): ByteArray? {
        return try {
            val digestInfo = byteArrayOf(
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
                0x05, 0x00, 0x04, 0x14
            ) + token
            val keySize = 256
            val em = ByteArray(keySize)
            em[1] = 0x01
            val ps = keySize - 3 - digestInfo.size
            for (i in 0 until ps) em[2 + i] = 0xFF.toByte()
            // em[2 + ps] = 0x00 (разделитель)
            System.arraycopy(digestInfo, 0, em, 2 + ps + 1, digestInfo.size)
            val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, priv)
            cipher.doFinal(em)
        } catch (e: Exception) {
            Log.e(TAG, "signToken failed", e)
            null
        }
    }

    // ─────────── Соединение и пакеты ───────────

    private var socket: Socket? = null
    private var rbuf = ByteArray(0)
    private var localId = 0

    /**
     * Подключиться к adbd и пройти авторизацию.
     * allowDialog=true — при неизвестном ключе отправить RSAPUBLICKEY и ждать диалог
     * (authTimeoutMs), false — быстро отказаться (для smoke-тестов без диалога на экране).
     */
    @Synchronized
    fun connect(
        ctx: Context,
        host: String = "127.0.0.1",
        port: Int = 5555,
        allowDialog: Boolean = false,
        authTimeoutMs: Int = 30_000,
    ): Boolean {
        socket?.let { s -> if (s.isConnected && !s.isClosed) return true }
        closeQuietly()

        val (priv, pub) = ensureKeys(ctx) ?: throw WireException("не удалось создать RSA-ключ")

        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 10_000
        socket = s
        rbuf = ByteArray(0)
        localId = 0

        send(CNXN, 0x01000001, 0x100000, "host::features=shell_v2,cmd".toByteArray())

        var p = recv(10_000) ?: run { closeQuietly(); throw WireException("adbd закрыл соединение") }
        if (p.cmd == AUTH) {
            if (p.a0 != AUTH_TOKEN) {
                closeQuietly(); throw WireException("неожиданный AUTH: тип ${p.a0}")
            }
            val sig = signToken(priv, p.data) ?: run { closeQuietly(); throw WireException("ошибка подписи токена") }
            send(AUTH, AUTH_SIGNATURE, 0, sig)
            p = recv(10_000) ?: run { closeQuietly(); throw WireException("adbd не ответил на подпись") }
            if (p.cmd == AUTH) {
                if (!allowDialog) {
                    closeQuietly()
                    throw WireException("ключ adb не авторизован (диалог отключён)")
                }
                val pk = (androidPubkey(ctx) ?: run { closeQuietly(); throw WireException("нет pubkey") })
                send(AUTH, AUTH_RSAPUBLICKEY, 0, pk.toByteArray(Charsets.US_ASCII) + byteArrayOf(0x0A))
                p = recv(authTimeoutMs) ?: run {
                    closeQuietly()
                    throw WireException("не авторизовано: подтверди диалог «Разрешить отладку» на экране")
                }
            }
        }
        if (p.cmd != CNXN) {
            closeQuietly()
            throw WireException("нет CNXN после авторизации (0x${p.cmd.toString(16)})")
        }
        Log.i(TAG, "adb wire: CNXN OK")
        return true
    }

    fun isConnected(): Boolean = socket?.let { it.isConnected && !it.isClosed } == true

    /** Выполнить команду в shell-домене. Возвращает (exitCode, stdout+stderr). */
    @Synchronized
    fun shell(
        ctx: Context,
        command: String,
        timeoutMs: Int = 300_000,
        allowDialog: Boolean = false,
    ): Pair<Int, String> {
        connect(ctx, allowDialog = allowDialog)

        localId += 1
        val local = localId
        send(OPEN, local, 0, "shell,v2,TERM=xterm-256color,raw:$command\u0000".toByteArray())

        var remote = -1
        while (remote < 0) {
            val p = recv(10_000) ?: throw WireException("нет OKAY на OPEN")
            when (p.cmd) {
                OKAY -> if (p.a1 == local || p.a0 == local) remote = if (p.a1 == local) p.a0 else p.a1
                CLSE -> if (p.a1 == local || p.a0 == local) throw WireException("поток закрыт сразу")
                else send(CLSE, p.a1, p.a0)  // подтверждаем чужой зависший CLSE
            }
        }

        send(WRTE, local, remote, byteArrayOf(4, 0, 0, 0, 0))  // window-size

        val acc = ByteArrayOutputStream()
        while (true) {
            val p = recv(timeoutMs) ?: break
            when (p.cmd) {
                WRTE -> { acc.write(p.data); send(OKAY, local, remote) }
                CLSE -> {
                    if (p.a0 == remote || p.a1 == local) { send(CLSE, local, remote); break }
                    else send(CLSE, p.a1, p.a0)
                }
                else -> {}
            }
        }
        return parseV2Stream(acc.toByteArray())
    }

    /** Передать файл на устройство: exec:cat > path, сырые чанки, CLSE = EOF. Проверяет размер. */
    @Synchronized
    fun deployFile(ctx: Context, localPath: String, remotePath: String): Boolean {
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
                    else -> {}
                }
            }

            var off = 0
            while (off < data.size) {
                val len = minOf(MAX_PAYLOAD, data.size - off)
                send(WRTE, local, remote, data.copyOfRange(off, off + len))
                off += len
                while (true) {
                    val p = recv(10_000) ?: return false
                    if (p.cmd == OKAY) break
                    if (p.cmd == WRTE) send(OKAY, local, remote)
                    if (p.cmd == CLSE) return false
                }
            }
            send(CLSE, local, remote)  // EOF

            while (true) {
                val p = recv(30_000) ?: break
                if (p.cmd == WRTE) send(OKAY, local, remote)
                if (p.cmd == CLSE) break
            }

            val (code, out) = shell(ctx, "wc -c < $remotePath")
            code == 0 && out.trim() == data.size.toString()
        } catch (e: Exception) {
            Log.e(TAG, "deployFile failed", e)
            closeQuietly()
            false
        }
    }

    @Synchronized
    fun closeQuietly() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        rbuf = ByteArray(0)
    }

    // ─────────── Внутреннее ───────────

    /** Разбор фреймов shell-v2: [id][len LE32][payload]; 1=stdout 2=stderr 3=exit. */
    private fun parseV2Stream(stream: ByteArray): Pair<Int, String> {
        val sb = StringBuilder()
        var exit = -1
        var i = 0
        while (i + 5 <= stream.size) {
            val id = stream[i].toInt() and 0xFF
            val len = readLeUInt(stream, i + 1)
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
        val s = socket ?: throw WireException("нет соединения")
        val out = s.getOutputStream()
        out.write(packet(cmd, a0, a1, data))
        out.flush()
    }

    private fun recv(timeoutMs: Int): Pkt? {
        val s = socket ?: return null
        val inp = s.getInputStream()
        s.soTimeout = timeoutMs
        while (rbuf.size < 24) {
            val chunk = ByteArray(65536)
            val n = try { inp.read(chunk) } catch (e: SocketTimeoutException) { return null } catch (e: Exception) {
                closeQuietly(); return null
            }
            if (n < 0) { closeQuietly(); return null }
            rbuf += chunk.copyOf(n)
        }
        val len = readLeUInt(rbuf, 12)
        if (len < 0 || len > 1_100_000) { closeQuietly(); return null }
        while (rbuf.size < 24 + len) {
            val chunk = ByteArray(65536)
            val n = try { inp.read(chunk) } catch (e: SocketTimeoutException) { return null } catch (e: Exception) {
                closeQuietly(); return null
            }
            if (n < 0) { closeQuietly(); return null }
            rbuf += chunk.copyOf(n)
        }
        val pkt = Pkt(readLeUInt(rbuf, 0), readLeUInt(rbuf, 4), readLeUInt(rbuf, 8),
            rbuf.copyOfRange(24, 24 + len))
        rbuf = rbuf.copyOfRange(24 + len, rbuf.size)
        return pkt
    }

    private fun packet(cmd: Int, a0: Int, a1: Int, data: ByteArray): ByteArray {
        val hdr = ByteArray(24)
        writeLeInt(hdr, 0, cmd)
        writeLeInt(hdr, 4, a0)
        writeLeInt(hdr, 8, a1)
        writeLeInt(hdr, 12, data.size)
        val crc = CRC32().apply { update(data) }.value.toInt()
        writeLeInt(hdr, 16, crc)
        writeLeInt(hdr, 20, cmd.inv())
        val out = ByteArrayOutputStream(24 + data.size)
        out.write(hdr)
        out.write(data)
        return out.toByteArray()
    }

    private fun writeLeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun writeLeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
    }

    private fun readLeUInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun bigIntLe(v: BigInteger, size: Int): ByteArray {
        val be = v.toByteArray()
        val trimmed = if (be.size > size) be.copyOfRange(be.size - size, be.size) else be
        val out = ByteArray(size)
        System.arraycopy(trimmed, 0, out, size - trimmed.size, trimmed.size)
        return out.reversedArray()
    }
}
