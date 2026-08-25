#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <android/log.h>

#define TAG "RootMyVivo"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * Патч vermagic строку в .ko файле под точный uname устройства.
 * Перезаписывает только строку в .modinfo секции.
 *
 * @param koPath путь к kernelsu.ko
 * @param release целевой uname -r (полный UTS_RELEASE)
 * @return true при успехе
 */
JNIEXPORT jboolean JNICALL
Java_com_rootmyvivo_core_KsuInstaller_nativePatchVermagic(
    JNIEnv *env, jclass clazz,
    jstring koPath, jstring release) {

    const char *path = (*env)->GetStringUTFChars(env, koPath, NULL);
    const char *rel = (*env)->GetStringUTFChars(env, release, NULL);

    FILE *f = fopen(path, "rb");
    if (!f) { LOGE("Cannot open %s", path); goto fail; }

    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);

    unsigned char *data = malloc(size);
    if (!data || fread(data, 1, size, f) != (size_t)size) {
        fclose(f); free(data); goto fail;
    }
    fclose(f);

    // Ищем "vermagic=" в .modinfo
    const char *needle = "vermagic=";
    long needle_len = 9;
    long pos = -1;
    for (long i = 0; i < size - needle_len; i++) {
        if (memcmp(data + i, needle, needle_len) == 0) {
            pos = i;
            break;
        }
    }
    if (pos < 0) {
        LOGE("vermagic= not found");
        free(data); goto fail;
    }

    long val_start = pos + needle_len;
    long val_end = val_start;
    while (val_end < size && data[val_end] != '\x00') val_end++;

    // Старый суффикс после версии ядра (SMP preempt mod_unload ...)
    char suffix[256] = {0};
    char old_ver[128] = {0};
    sscanf((char*)data + val_start, "%127s %255[^\n]",
           old_ver, suffix);

    // Собираем новый vermagic
    char new_vm[512];
    snprintf(new_vm, sizeof(new_vm), "%s %s", rel, suffix);
    long new_len = strlen(new_vm);
    long space = val_end - val_start;

    LOGD("old_vermagic=%s new=%s space=%ld", old_ver, new_vm, space);

    if ((long)strlen(new_vm) > (size_t)space) {
        LOGE("new vermagic (%zu) > space (%ld)", strlen(new_vm), space);
        free(data); goto fail;
    }

    // Перезаписываем + заполняем нулями до конца старой строки
    memcpy(data + val_start, new_vm, new_len);
    memset(data + val_start + new_len, 0, space - new_len);

    // Записываем обратно
    f = fopen(path, "wb");
    if (!f || fwrite(data, 1, size, f) != (size_t)size) {
        if(f) fclose(f);
        free(data); goto fail;
    }
    fclose(f);
    free(data);

    LOGD("vermagic patched OK");
    (*env)->ReleaseStringUTFChars(env, koPath, path);
    (*env)->ReleaseStringUTFChars(env, release, rel);
    return JNI_TRUE;

fail:
    (*env)->ReleaseStringUTFChars(env, koPath, path);
    (*env)->ReleaseStringUTFChars(env, release, rel);
    return JNI_FALSE;
}
