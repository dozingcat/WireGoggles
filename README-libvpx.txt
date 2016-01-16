WireGoggles uses the libvpx library to create WebM videos. This repository
includes only the subset of libvpx headers and source needed for the NDK code in
wg_video.c to build. The code needs to link against the full libvpx library,
which is included as a binary for ARM and x86 in jni/libvpx_prebuilt_libs.
Building an Android-compatible library from the WebM sources is somewhat tricky;
this is what I did.

0. Prerequisites: I was not able to build libvpx successfully on a Mac, so I
used a Linux VM (64-bit Mint Linux 17.3, but anything reasonably recent should
work). You will need to install the Android NDK and the yasm assember, and
possibly other compiler packages so that you can build for ARM and x86.

1. Clone the libvpx repository at https://chromium.googlesource.com/webm/libvpx
(i.e. "git clone https://chromium.googlesource.com/webm/libvpx"). Building from
head should work assuming there are no incompatible API changes, but if you
want to use the exact code that the library in the WireGoggles repository was
built against, check out revision ea48370a500537906d62544ca4ed75301d79e772.

2. Anywhere you see [NDK_PATH] in the following commands, replace it
with the full path to the Android NDK directory, for example
"/Users/foo/android/android-ndk-r10e".

To build the ARM library, cd to libvpx and execute:

===============================================================================
./configure --target=armv7-android-gcc --sdk-path=[NDK_PATH] \
--disable-examples --disable-docs --disable-vp8-decoder --disable-vp9-decoder \
--enable-vp8-encoder --disable-vp9-encoder --disable-vp10 --disable-neon \
--disable-neon-asm --disable-runtime-cpu-detect
===============================================================================

Then just execute "make", which if all goes well will produce libvpx.a in the
main directory. This is the static library that goes in
jni/libvpx_prebuilt_libs/armeabi-v7a. Run "make clean" before building x86.

3. x86 is trickier because we can't use the default NDK runtime. See
http://stackoverflow.com/questions/28010753/android-ndk-returns-an-error-undefined-reference-to-rand

Run these commands:

===============================================================================
export PATH=[ANDROID_NDK_PATH]/toolchains/x86-4.8/prebuilt/linux-x86_64/bin:$PATH

ASFLAGS="-D__ANDROID__" CROSS=i686-linux-android- \
LDFLAGS="--sysroot=[NDK_PATH]/platforms/android-9/arch-x86" ./configure \
--target=x86-android-gcc --sdk-path=[NDK_PATH] --disable-examples \
--disable-docs --disable-vp8-decoder --disable-vp9-decoder \
--enable-vp8-encoder --disable-vp9-encoder --disable-vp10 \
--disable-runtime-cpu-detect --disable-mmx --disable-sse --disable-sse2 \
--disable-sse3 --disable-ssse3 --disable-sse4_1 \
--extra-cflags="--sysroot=[NDK_PATH]/platforms/android-9/arch-x86"
===============================================================================

Now "make" should produce the libvpx.a static library for x86.

The configure commands above enable only the VP8 encoder and disble VP9 because
I found that the size and quality difference between them wasn't noticeable, but
VP9 is much slower to encode. If you would like to try VP9 you can change the
flags to --enable-vp9-encoder and set WG_USE_VP9 to 1 in wg_video.c.
