include chromium-browser.inc

inherit distro_features_check

OUTPUT_DIR = "out/${CHROMIUM_BUILD_TYPE}"
B = "${S}/${OUTPUT_DIR}"

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

# These are present as their own variables, since they have changed between versions
# a few times in the past already; making them variables makes it easier to handle that
CHROMIUM_X11_GYP_DEFINES ?= ""

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
