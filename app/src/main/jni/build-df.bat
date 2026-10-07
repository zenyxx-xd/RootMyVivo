@echo off
rem DirtyFrag native binaries build for RootMyVivo APK (NDK r29, API 34).
rem Run from app\app\src\main\jni (relative .incbin paths ko/ and splicehelper).
rem Output: app\app\src\main\jniLibs\arm64-v8a\{libdfroot.so, libbootstrap.so}
setlocal
set NDK=C:\neo11\tools\android-ndk-r29
set CC=%NDK%\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android34-clang.cmd
set STRIP=%NDK%\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe
set OUT=C:/neo11/app/app/src/main/jniLibs/arm64-v8a

rem splicehelper: freestanding static (embedded via .incbin into libdfroot)
call "%CC%" splicehelper.c -o splicehelper -nodefaultlibs -nostartfiles -ffreestanding -static
if errorlevel 1 goto :err
call "%STRIP%" splicehelper
if errorlevel 1 goto :err

rem libdfroot.so: PIE executable (AGP puts it into nativeLibraryDir)
call "%CC%" -fPIE -I. dfexp.c libcxx.S elf_parser.c -o "%OUT%\libdfroot.so" -llog -Wl,-z,max-page-size=16384
if errorlevel 1 goto :err

rem libbootstrap.so: bootstrap binary executed from the ko (root context)
call "%CC%" -fPIE bootstrap.c -o "%OUT%\libbootstrap.so" -Wl,-z,max-page-size=16384
if errorlevel 1 goto :err

echo ALL OK
exit /b 0
:err
echo BUILD FAILED
exit /b 1
