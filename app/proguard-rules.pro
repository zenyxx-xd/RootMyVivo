-keep class com.rootmyvivo.core.native.** { *; }
-keepclassmembers class com.rootmyvivo.core.KsuInstaller { nativePatchVermagic(...); }

# Shizuku UserService: конструкторы должны выжить после R8
-keep class com.rootmyvivo.core.ShellServiceImpl {
    public <init>(...);
}
-keep class com.rootmyvivo.core.IShellService { *; }
-keep class com.rootmyvivo.core.IShellService$* { *; }
