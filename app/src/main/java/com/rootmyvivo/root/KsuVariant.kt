package com.rootmyvivo.root

/** Варианты KernelSU — описания локализуются в UI по id. */
enum class KsuVariant(
    val id: String,
    val displayName: String,
    val packageName: String,
    val repo: String,
    /** Прошлые имена пакета — для скана установленных (переименования). */
    val aliases: List<String> = emptyList(),
) {
    KERNELSU("kernelsu", "KernelSU", "me.weishu.kernelsu", "tiann/KernelSU"),
    KSU_NEXT("ksunext", "KernelSU Next", "com.rifsxd.ksunext", "KernelSU-Next/KernelSU-Next"),
    // SukiSU Ultra → SukiSU Nova (ноябрь 2026): новый пакет и репозиторий,
    // старый com.sukisu.ultra оставлен алиасом для уже установленных
    SUKISU("sukisu", "SukiSU Nova", "com.sukisu.nova", "SukiSU-Nova/SukiSU-Nova", listOf("com.sukisu.ultra")),
    RESUKISU("resukisu", "ReSukiSU", "com.resukisu.resukisu", "ReSukiSU/ReSukiSU"),
    FINASU("finasu", "FinaSU", "me.yuki.fina", "LyraVoid/FinaSU");

    companion object {
        fun byId(id: String): KsuVariant = entries.find { it.id == id } ?: RESUKISU

        /** Вариант по фактическому пакету (для скана установленных). */
        fun byPackage(pkg: String): KsuVariant? =
            entries.find { it.packageName == pkg } ?: entries.find { pkg in it.aliases }
    }
}
