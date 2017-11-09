include chromium.inc

inherit distro_features_check gtk-icon-cache

OUTPUT_DIR = "out/${CHROMIUM_BUILD_TYPE}"
B = "${S}/${OUTPUT_DIR}"

# Append instead of assigning; the gtk-icon-cache class inherited above also
# adds packages to DEPENDS.
DEPENDS += " \
    alsa-lib \
    cairo \
    gperf-native \
    gtk+ \
    libdrm \
    libxi \
    libxss \
    libxtst \
    ninja-native \
    nss \
    pango \
    pciutils \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'pulseaudio', '', d)} \
    virtual/libgl \
"
DEPENDS_append_libc-musl = " libexecinfo"

SRC_URI += "\
        file://add_missing_stat_h_include.patch \
        file://0003-Remove-hard-coded-values-for-CC-and-CXX.patch \
        file://0005-Override-root-filesystem-access-restriction.patch \
        file://0011-Replace-readdir_r-with-readdir.patch \
        ${@bb.utils.contains('PACKAGECONFIG', 'ignore-lost-context', 'file://0001-Remove-accelerated-Canvas-support-from-blacklist.patch', '', d)} \
        file://m32.patch \
"

LIC_FILES_CHKSUM = "file://LICENSE;md5=0fca02217a5d49a14dfe2d11837bb34d"
SRC_URI[md5sum] = "3f596ecbd6a39d5ada29f11780ec6dcf"
SRC_URI[sha256sum] = "f038e72cbd8b7383d13c286329623fda8d6d48f45fa2d964e554b5565283ad71"

# For now, we need X11 for Chromium to build and run.
REQUIRED_DISTRO_FEATURES = "x11"

# PACKAGECONFIG options
# ^^^^^^^^^^^^^^^^^^^^^
# * use-egl: (on by default)
#       Without this packageconfig, the Chromium build will use GLX for
#       creating an OpenGL context in X11, and regular OpenGL for painting
#       operations. Neither are desirable on embedded platforms. With this
#       packageconfig, EGL and OpenGL ES 2.x are used instead.
#
# * component-build: (off by default)
#       Enables component build mode. By default, all of Chromium (with the
#       exception of FFmpeg) is linked into one big binary. The linker step
#       requires at least 8 GB RAM. Component mode was created to facilitate
#       development and testing, since with it, there is not one big binary;
#       instead, each component is linked to a separate shared object. Use
#       component mode for development, testing, and in case the build machine
#       is not a 64-bit one, or has less than 8 GB RAM.
#
# * ignore-lost-context: (off by default)
#       There is a flaw in the HTML Canvas specification. When the canvas'
#       backing store is some kind of hardware resource like an OpenGL
#       texture, this resource might get lost. In case of OpenGL textures,
#       this happens when the OpenGL context gets lost. The canvas should then
#       be repainted, but nothing in the Canvas standard reflects that. This
#       packageconfig is to be used if the underlying OpenGL (ES) drivers do
#       not lose the context, or if losing the context is considered okay
#       (note that canvas contents can vanish then).
#
# * impl-side-painting: (off by default)
#       This is a new painting mechanism. Still in
#       development stages, it can improve performance See
#       http://www.chromium.org/developers/design-documents/impl-side-painting
#       for more.
#
# * kiosk-mode: (off by default)
#       Enable this option if you want your browser to start up full-screen,
#       without any menu bars, without any clutter, and without any initial
#       start-up indicators.
#
# * proprietary-codecs: (off by default)
#       Enable this option if you want to add support for additional proprietary
#       codecs, most notably MPEG standards (h.264, h.265, MP4, MP3, AAC, MPEG-2 ..)

PACKAGECONFIG ??= "use-egl"

# this makes sure the dependencies for the EGL mode are present; otherwise, the configure scripts
# automatically and silently fall back to GLX
PACKAGECONFIG[use-egl] = ",,virtual/egl virtual/libgles2"

# Empty PACKAGECONFIG options listed here to avoid warnings.
# The .bb file should use these to conditionally add patches
# and command-line switches (extra dependencies should not
# be necessary but are OK to add).
PACKAGECONFIG[component-build] = ""
PACKAGECONFIG[ignore-lost-context] = ""
PACKAGECONFIG[impl-side-painting] = ""
PACKAGECONFIG[kiosk-mode] = ""
PACKAGECONFIG[proprietary-codecs] = ""

# These are present as their own variables, since they have changed between versions
# a few times in the past already; making them variables makes it easier to handle that
CHROMIUM_X11_GYP_DEFINES ?= ""

GYP_DEFINES += "release_extra_cflags='-Wno-error=unused-local-typedefs' sysroot='' \
	${@bb.utils.contains("AVAILTUNES", "mips", "", "release_extra_cflags='-fno-delete-null-pointer-checks'", d)}"
GYP_DEFINES_append_x86 = " generate_character_data=0"

# Disable activation of field trial tests that can cause problems in
# production.
# See https://groups.google.com/a/chromium.org/d/msg/chromium-packagers/ECWC57W7E0k/9Kc5UAmyDAAJ
GYP_DEFINES += "-Dfieldtrial_testing_like_official_build=1"

EXTRA_OEGYP = " \
	-Dangle_use_commit_id=0 \
	-Dclang=0 \
	-Dhost_clang=0 \
	-Ddisable_fatal_linker_warnings=1 \
	-Dwerror= \
	-Dv8_use_external_startup_data=0 \
	-Dlinux_use_bundled_gold=0 \
	-Dlinux_use_bundled_binutils=0 \
	-Duse_gconf=0 \
	-Duse_cups=0 \
	-Duse_pulseaudio=${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', '1', '0', d)} \
	-I ${WORKDIR}/oe-defaults.gypi \
	-I ${WORKDIR}/include.gypi \
	${@bb.utils.contains('PACKAGECONFIG', 'component-build', '-Dcomponent=shared_library', '', d)} \
	${@bb.utils.contains('PACKAGECONFIG', 'proprietary-codecs', '-Dproprietary_codecs=1 -Dffmpeg_branding=Chrome', '', d)} \
	-Dlinux_use_gold_flags=${@bb.utils.contains('DISTRO_FEATURES', 'ld-is-gold', '1', '0', d)} \
	-f ninja \
"

# API keys for accessing Google services. By default, we use an invalid key
# only to prevent the "you are missing an API key" infobar from being shown on
# startup.
# See https://dev.chromium.org/developers/how-tos/api-keys for more information
# on how to obtain your own keys. You can then set the variables below in
# local.conf or a .bbappend file.
GOOGLE_API_KEY ??= "invalid-api-key"
GOOGLE_DEFAULT_CLIENT_ID ??= "invalid-client-id"
GOOGLE_DEFAULT_CLIENT_SECRET ??= "invalid-client-secret"
EXTRA_OEGYP += "\
        -Dgoogle_api_key=${GOOGLE_API_KEY} \
        -Dgoogle_default_client_id=${GOOGLE_DEFAULT_CLIENT_ID} \
        -Dgoogle_default_client_secret=${GOOGLE_DEFAULT_CLIENT_SECRET} \
"

# ARM builds need special additional flags (see ${S}/build/config/arm.gni).
# If we do not pass |arm_arch| and friends to GN, it will deduce a value that
# will then conflict with TUNE_CCARGS and CC.
def get_arm_version(arm_arch):
    import re
    try:
        return re.match(r'armv(\d).*', arm_arch).group(1)
    except IndexError:
        bb.fatal('Unrecognized ARM architecture value: %s' % arm_arch)
def get_compiler_flag(params, param_name, d):
    """Given a sequence of compiler arguments in |params|, returns the value of
    an option |param_name| or an empty string if the option is not present."""
    for param in params:
      if param.startswith(param_name):
        return param.split('=')[1]
    return ''
ARM_ARCH = "${@get_compiler_flag(d.getVar('TUNE_CCARGS').split(), '-march', d)}"
ARM_FLOAT_ABI = "${@bb.utils.contains('TUNE_FEATURES', 'callconvention-hard', 'hard', 'softfp', d)}"
ARM_FPU = "${@get_compiler_flag(d.getVar('TUNE_CCARGS').split(), '-mfpu', d)}"
ARM_TUNE = "${@get_compiler_flag(d.getVar('TUNE_CCARGS').split(), '-mcpu', d)}"
ARM_VERSION = "${@get_arm_version(d.getVar('ARM_ARCH'))}"
GYP_DEFINES_append_arm = " \
        -Darm_arch=${ARM_ARCH} \
        -Darm_float_abi=${ARM_FLOAT_ABI} \
        -Darm_fpu=${ARM_FPU} \
        -Darm_tune=${ARM_TUNE} \
        -Darm_version=${ARM_VERSION} \
"

CHROMIUM_EXTRA_ARGS ?= " \
        ${@bb.utils.contains('PACKAGECONFIG', 'use-egl', '--use-gl=egl', '', d)} \
        ${@bb.utils.contains('PACKAGECONFIG', 'ignore-lost-context', '--gpu-no-context-lost', '', d)} \
        ${@bb.utils.contains('PACKAGECONFIG', 'impl-side-painting', '--enable-gpu-rasterization --enable-impl-side-painting', '', d)} \
        ${@bb.utils.contains('PACKAGECONFIG', 'kiosk-mode', '--start-fullscreen --kiosk --no-first-run --incognito', '', d)} \
"

python() {
    d.appendVar('GYP_DEFINES', ' %s ' % d.getVar('CHROMIUM_X11_GYP_DEFINES', True))
}

do_configure_prepend_libc-musl() {
	for f in `find ${S}/third_party/ffmpeg/chromium/config/{Chrome,Chromium}/linux/ -name config.h -o -name config.asm`; do
		sed -i -e "s:define HAVE_SYSCTL 1:define HAVE_SYSCTL 0:g" $f
	done
	sed -i -e "s:define HAVE_STRUCT_MALLINFO 1:/*undef HAVE_STRUCT_MALLINFO */:g" ${S}/third_party/tcmalloc/chromium/src/config_linux.h
}

do_configure() {
	cd ${S}
	GYP_DEFINES="${GYP_DEFINES}" export GYP_DEFINES
	# replace LD with CXX, to workaround a possible gyp issue?
	LD="${CXX}" export LD
	CC="${CC}" export CC
	CXX="${CXX}" export CXX
	CC_host="${BUILD_CC}" export CC_host
	CXX_host="${BUILD_CXX}" export CXX_host

	build/gyp_chromium --depth=. ${EXTRA_OEGYP}
}

do_compile() {
	ninja -v ${PARALLEL_MAKE} chrome chrome_sandbox chromedriver
}
do_compile[progress] = "outof:^\[(\d+)/(\d+)\]\s+"

do_install() {
	install -d ${D}${bindir}
	install -d ${D}${bindir}/${BPN}
	install -d ${D}${bindir}/${BPN}/locales
	install -d ${D}${datadir}
	install -d ${D}${datadir}/applications
	install -d ${D}${datadir}/icons
	install -d ${D}${datadir}/icons/hicolor
	install -d ${D}${sbindir}

	install -m 0755 ${WORKDIR}/google-chrome ${D}${bindir}/google-chrome
	install -m 4755 chrome_sandbox ${D}${sbindir}/chrome-devel-sandbox
	install -m 0755 chrome ${D}${bindir}/${BPN}/chrome
	install -m 0644 icudtl.dat ${D}${bindir}/${BPN}/icudtl.dat

	# Process and install Chromium's template .desktop file.
	sed -e "s,@@MENUNAME@@,Chromium Browser,g" \
	    -e "s,@@PACKAGE@@,chromium,g" \
	    -e "s,@@USR_BIN_SYMLINK_NAME@@,google-chrome,g" \
	    ${S}/chrome/installer/linux/common/desktop.template > google-chrome.desktop
	install -m 0644 google-chrome.desktop ${D}${datadir}/applications/google-chrome.desktop

	# Install icons.
	for size in 16 22 24 32 48 64 128 256; do
		install -d ${D}${datadir}/icons/hicolor/${size}x${size}
		install -d ${D}${datadir}/icons/hicolor/${size}x${size}/apps
		for dirname in "chromium" "default_100_percent/chromium"; do
			icon="${S}/chrome/app/theme/${dirname}/product_logo_${size}.png"
			if [ -f "${icon}" ]; then
				install -m 0644 "${icon}" \
					${D}${datadir}/icons/hicolor/${size}x${size}/apps/chromium.png
			fi
		done
	done

	# Chromium *.pak files
	install -m 0644 chrome_*.pak ${D}${bindir}/${BPN}/
	install -m 0644 resources.pak ${D}${bindir}/${BPN}/resources.pak

	# Locales.
	install -m 0644 locales/*.pak ${D}${bindir}/${BPN}/locales/

	# Add extra command line arguments to google-chrome script by modifying
	# the dummy "CHROME_EXTRA_ARGS" line
	sed -i "s/^CHROME_EXTRA_ARGS=\"\"/CHROME_EXTRA_ARGS=\"${CHROMIUM_EXTRA_ARGS}\"/" ${D}${bindir}/google-chrome

	# update ROOT_HOME with the root user's $HOME
	sed -i "s#ROOT_HOME#${ROOT_HOME}#" ${D}${bindir}/google-chrome

	# Always adding this libdir (not just with component builds), because the
	# LD_LIBRARY_PATH line in the google-chromium script refers to it
	install -d ${D}${libdir}/${BPN}/
	if [ -n "${@bb.utils.contains('PACKAGECONFIG', 'component-build', 'component-build', '', d)}" ]; then
		install -m 0755 lib/*.so ${D}${libdir}/${BPN}/
	fi

	# ChromeDriver.
	install -m 0755 chromedriver ${D}${bindir}/chromedriver
}

PACKAGES =+ "${PN}-chromedriver"

FILES_${PN}-chromedriver = "${bindir}/chromedriver"

FILES_${PN} = " \
        ${bindir} \
        ${bindir}/${BPN} \
        ${bindir}/${BPN}/*.pak \
        ${bindir}/${BPN}/locales/*.pak \
        ${datadir}/applications \
        ${datadir}/icons/hicolor/*x*/apps/chromium.png \
        ${libdir} \
        ${libdir}/${BPN}/ \
        ${sbindir}/ \
"

PACKAGE_DEBUG_SPLIT_STYLE = "debug-without-src"

# chromium-54.0.2810.2: ELF binary 'i586-oe-linux/chromium/54.0.2810.2-r0/packages-split/chromium/usr/bin/chromium/chrome' has relocations in .text [textrel]
INSANE_SKIP_${PN} = "ldflags textrel"
SOLIBS = ".so"
FILES_SOLIBSDEV = ""

# There is no need to ship empty -dev packages.
ALLOW_EMPTY_${PN}-dev = "0"
