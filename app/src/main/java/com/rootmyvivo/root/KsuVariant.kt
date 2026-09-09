package com.rootmyvivo.root

/** Варианты KernelSU — описания локализуются в UI по id. */
enum class KsuVariant(
    val id: String,
    val displayName: String,
    val packageName: String,
    val repo: String,
) {
    KERNELSU("kernelsu", "KernelSU", "me.weishu.kernelsu", "tiann/KernelSU"),
    KSU_NEXT("ksunext", "KernelSU Next", "com.rifsxd.ksunext", "KernelSU-Next/KernelSU-Next"),
    SUKISU("sukisu", "SukiSU Ultra", "com.sukisu.ultra", "SukiSU-Ultra/SukiSU-Ultra"),
    RESUKISU("resukisu", "ReSukiSU", "com.resukisu.resukisu", "ReSukiSU/ReSukiSU");

    companion object {
        fun byId(id: String): KsuVariant = entries.find { it.id == id } ?: RESUKISU
    }
}
