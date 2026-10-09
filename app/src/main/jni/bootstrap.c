/* Бутстрап: запускается ko-модулем в root-контексте после срабатывания хука.
 * Читает df.conf (key=value) из device-protected папки приложения,
 * затем ksud late-load. Путь к себе зашит в lkm/dfroot.c — совпадает. */
#include <dirent.h>
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

/* Пометить все установленные модули disabled до ksud: сломанный модуль
 * иначе загрузится на следующей загрузке и зациклит устройство. */
static int disable_modules(void)
{
    DIR *dir = opendir(MODULES_DIR);
    if (!dir)
        return -1;

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

    /* Менеджер ставим СРАЗУ из root-контекста: su-бинарник живёт в
     * менеджере, и без него приложение не может поставить APK ни через
     * su-деплой, ни (на части прошивок) через системный установщик из
     * фона. APK приложение докачивает ДО запуска эксплойта. */
    const char *manager_apk = "/data/user_de/0/com.rootmyvivo/manager.apk";
    if (access(manager_apk, F_OK) == 0) {
        char *inst[] = {
            "/system/bin/sh", "-c",
            "pm install -r /data/user_de/0/com.rootmyvivo/manager.apk "
            ">/dev/null 2>&1 || pm install -r --no-verify "
            "/data/user_de/0/com.rootmyvivo/manager.apk >/dev/null 2>&1",
            NULL,
        };
        run(inst);
    }

    touch("/dev/dfm4");
    char **late_load;
    if (soft_reboot)
        late_load = (char *[]){ KSUD, "late-load", "--package-name", su_manager, "--soft-reboot", NULL };
    else
        late_load = (char *[]){ KSUD, "late-load", "--package-name", su_manager, NULL };
    if (run(late_load) == 0)
        touch("/dev/dfm5");
    else
        touch("/dev/dfme1");

    return 0;
}
