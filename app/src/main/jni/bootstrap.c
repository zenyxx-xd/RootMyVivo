/* Бутстрап: запускается ko-модулем в root-контексте после срабатывания хука.
 * Читает df.conf (key=value) из device-protected папки приложения,
 * затем ksud late-load. Путь к себе зашит в lkm/dfroot.c — совпадает. */
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define BLKROSET   0x125d
#define KSUD       "/data/user_de/0/com.rootmyvivo/ksud"
#define CONF_PATH  "/data/user_de/0/com.rootmyvivo/df.conf"
#define MODULES_DIR "/data/adb/modules"

static int conf_value(const char *buf, const char *key, char *out, size_t out_size)
{
    char needle[64];
    snprintf(needle, sizeof(needle), "%s=", key);
    char *p = strstr(buf, needle);
    if (!p) return -1;
    p += strlen(needle);
    char *end = strchr(p, '\n');
    if (!end) end = p + strlen(p);
    size_t len = end - p;
    if (len == 0 || len >= out_size) return -1;
    memcpy(out, p, len);
    out[len] = '\0';
    return 0;
}

static int conf_true(const char *buf, const char *key)
{
    char v[16];
    if (conf_value(buf, key, v, sizeof(v)) != 0) return 0;
    return strcmp(v, "1") == 0 || strcmp(v, "true") == 0;
}

static int read_conf(char *su_manager, size_t su_manager_size, int *soft_reboot,
                     int *disable_modules)
{
    int fd = open(CONF_PATH, O_RDONLY);
    if (fd < 0) return -1;

    char buf[4096];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';

    if (conf_value(buf, "su_manager", su_manager, su_manager_size) != 0) {
        /* Менеджер может быть не установлен — ksud late-load работает
         * и без него; дефолт — KernelSU, менеджер доставит приложение */
        strcpy(su_manager, "me.weishu.kernelsu");
    }
    *soft_reboot = conf_true(buf, "soft_reboot");
    *disable_modules = conf_true(buf, "disable_modules");
    return 0;
}

static int adopt_zygote_env(void)
{
    FILE *f = popen("pidof zygote64 zygote", "r");
    if (!f) return -1;
    int pid = 0;
    fscanf(f, "%d", &pid);
    pclose(f);
    if (!pid) return -1;

    char path[32];
    snprintf(path, sizeof(path), "/proc/%d/environ", pid);
    int fd = open(path, O_RDONLY);
    if (fd < 0) return -1;
    static char buf[16384];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';
    for (char *p = buf, *end = buf + n; p < end; p += strlen(p) + 1)
        putenv(p);
    return 0;
}

static int should_ro(const char *name)
{
    size_t len = strlen(name);
    if (!strcmp(name, "super"))  return 1;
    if (!strcmp(name, "misc"))   return 1;
    if (!strcmp(name, "steady")) return 1;
    if (len >= 2 && name[len - 2] == '_' &&
        (name[len - 1] == 'a' || name[len - 1] == 'b'))
        return 1;
    return 0;
}

static int set_partitions_ro(void)
{
    DIR *dir = opendir("/dev/block/by-name");
    if (!dir)
        return -1;

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (!should_ro(ent->d_name))
            continue;

        char path[128];
        snprintf(path, sizeof(path), "/dev/block/by-name/%s", ent->d_name);

        int fd = open(path, O_RDONLY);
        if (fd < 0)
            continue;

        struct stat st;
        if (fstat(fd, &st) == 0 && S_ISBLK(st.st_mode)) {
            int on = 1;
            ioctl(fd, BLKROSET, &on);
        }
        close(fd);
    }

    closedir(dir);
    return 0;
}

static int run(char *const argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        return -1;
    if (pid == 0) {
        execv(argv[0], argv);
        _exit(127);
    }
    int status;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

static void touch(const char *path)
{
    int fd = open(path, O_CREAT | O_WRONLY, 0666);
    if (fd >= 0)
        close(fd);
}

/* Живой ли ещё ksud late-load (как в оригинальном DirtyFrag): ждём его
 * завершения перед soft-reboot — иначе перезапуск system_server
 * обгонит поднятие KSU. */
static int late_load_running(void)
{
    DIR *d = opendir("/proc");
    if (!d)
        return 0;
    struct dirent *e;
    char path[64], buf[256];
    int found = 0;
    while ((e = readdir(d)) != NULL) {
        char *end;
        strtol(e->d_name, &end, 10);
        if (*end) continue;
        snprintf(path, sizeof(path), "/proc/%s/cmdline", e->d_name);
        int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        int n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n <= 0) continue;
        buf[n] = '\0';
        for (int i = 0; i < n; i++)
            if (!buf[i]) buf[i] = ' ';
        if (strstr(buf, "ksud") && strstr(buf, "late-load")) {
            found = 1;
            break;
        }
    }
    closedir(d);
    return found;
}

/* Запуск команды с переводом в SELinux-контекст (как run_ctx в
 * оригинальном DirtyFrag): /proc/self/attr/exec до execv. */
static int run_ctx(const char *ctx, char *const argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        return -1;
    if (pid == 0) {
        if (ctx) {
            int fd = open("/proc/self/attr/exec", O_WRONLY);
            if (fd >= 0) {
                write(fd, ctx, strlen(ctx));
                close(fd);
            }
        }
        execv(argv[0], argv);
        _exit(127);
    }
    int status;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

/* Пометить все установленные модули disabled до ksud: сломанный модуль
 * иначе загрузится на следующей загрузке и зациклит устройство.
 * DirtyFrag 1.18: менеджер без единого модуля вообще не имеет MODULES_DIR,
 * а ENOENT уже удовлетворяет «ничего не включено» — раньше это гасило весь
 * рут до запуска ksud. Всё, кроме ENOENT (права, I/O) — реальный сбой. */
static int disable_modules(void)
{
    DIR *dir = opendir(MODULES_DIR);
    if (!dir) {
        if (errno == ENOENT)
            return 0;
        return -1;
    }

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (ent->d_name[0] == '.')
            continue;
        char path[256];
        snprintf(path, sizeof(path), MODULES_DIR "/%s/disable", ent->d_name);
        touch(path);
    }
    closedir(dir);
    return 0;
}

int main(void)
{
    char su_manager[256];
    int soft_reboot, disable_mods;
    if (read_conf(su_manager, sizeof(su_manager), &soft_reboot, &disable_mods) != 0) {
        touch("/dev/dfme0");
        return 1;
    }
    touch("/dev/dfm1");

    touch("/dev/dfm7");
    if (adopt_zygote_env() == 0)
        touch("/dev/dfm2");
    else
        touch("/dev/dfmw0");

    touch("/dev/dfm8");
    if (set_partitions_ro() == 0)
        touch("/dev/dfm3");
    else
        touch("/dev/dfmw1");

    if (disable_mods && disable_modules() != 0)
        touch("/dev/dfmw2");

    touch("/dev/dfm4");
    /* --allow-shell: shell-домен adb получает root БЕЗ диалога менеджера
     * (наш основной канал: persist-порт + ключ живут через ребуты).
     * Флаг поддерживают не все ksud — при отказе повторяем без него */
    char *late_load_flag[] = { KSUD, "late-load", "--package-name", su_manager, "--allow-shell", NULL };
    char *late_load[] = { KSUD, "late-load", "--package-name", su_manager, NULL };
    int ll_rc = run(late_load_flag);
    if (ll_rc != 0)
        ll_rc = run(late_load);
    if (ll_rc == 0) {
        touch("/dev/dfm5");

        /* Userspace из root-контекста: ksud install создаёт su-клиент и
         * правила. Менеджер НЕ запускаем: авторизацию он получил через
         * --package-name, а авто-открытие приложения после рута — лишнее */
        char *inst[] = { KSUD, "install", NULL };
        run(inst);

        /* Root-канал для приложения: форки прячут /system/bin/su (susfs),
         * и приложению нечем выполнить root-команду. Оставляем fifo-демон
         * в приватной папке приложения (только оно может писать): шлёт
         * строку в rmv.in — демон выполняет её от root, ответ в rmv.out,
         * код в rmv.rc, счётчик в rmv.seq. Живёт до перезагрузки */
        {
            char *daemon[] = {
                "/system/bin/sh", "-c",
                "D=/data/user_de/0/com.rootmyvivo; "
                "rm -f $D/rmv.in $D/rmv.busy; "
                "mkfifo $D/rmv.in 2>/dev/null; "
                "touch $D/rmv.seq; "
                "exec 3<> $D/rmv.in; "
                "while IFS= read -r line; do "
                "  echo busy > $D/rmv.busy; "
                "  sh -c \"$line\" > $D/rmv.out 2>&1; echo $? > $D/rmv.rc; "
                "  rm -f $D/rmv.busy; "
                "  echo $(( $(cat $D/rmv.seq 2>/dev/null || echo 0) + 1 )) > $D/rmv.seq; "
                "done <&3 &",
                NULL,
            };
            run(daemon);
        }

        /* SukiSU/FinaSU ksud сам запускает менеджер после late-load —
         * пользователю это не нужно. Фоновый ватчер прибивает его через
         * 5с (позже кнопка перезагрузки поднимет его сама при запросе) */
        if (su_manager[0]) {
            char wcmd[512];
            snprintf(wcmd, sizeof(wcmd),
                     "(sleep 5; am force-stop %s) >/dev/null 2>&1 &",
                     su_manager);
            char *watch[] = { "/system/bin/sh", "-c", wcmd, NULL };
            run(watch);
        }

        /* Авто-софт-ребут как «auto reboot» в оригинальном DirtyFrag:
         * ждём завершения late-load, затем `ksud soft-reboot` в
         * ksu-контексте — БЕЗ adb и БЕЗ подтверждения в менеджере.
         * ksud ставит себя в /data/adb/ksud при late-load; если его
         * там нет — используем стейджнутый. */
        if (soft_reboot) {
            while (late_load_running()) sleep(1);
            if (access("/data/adb/ksud", X_OK) == 0)
                run_ctx("u:r:ksu:s0", (char *[]){ "/data/adb/ksud", "soft-reboot", NULL });
            else
                run_ctx("u:r:ksu:s0", (char *[]){ KSUD, "soft-reboot", NULL });
        }
    } else {
        touch("/dev/dfme1");
    }

    return 0;
}