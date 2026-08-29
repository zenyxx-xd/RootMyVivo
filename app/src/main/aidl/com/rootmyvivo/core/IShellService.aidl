package com.rootmyvivo.core;

interface IShellService {
    void destroy() = 16777114; // зарезервировано Shizuku-сервером
    void exit() = 1;

    /** Выполнить shell-команду. Возвращает "EXIT=<code>\n<output>". */
    String exec(String command) = 2;

    /** Дописать чанк байтов в файл (путь, offset, данные). Возвращает true при успехе. */
    boolean writeFileChunk(String path, long offset, in byte[] data) = 3;

    /** Проверка живости. */
    String ping() = 4;
}
