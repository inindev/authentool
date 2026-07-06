# Authentool - command-line build, no Xcode UI required.
#
#   make           release build (default) -> build/Build/Products/Release/authentool.app
#   make release   release build
#   make debug     debug build              -> build/Build/Products/Debug/authentool.app
#   make run       build, then launch it from Finder context (no debugger)
#   make test      run the AuthentoolCore package tests
#   make clean     remove build artifacts
#
# Printing under App Sandbox needs com.apple.security.print, which Xcode's
# build-setting entitlements don't emit. We bypass that by signing with our own
# entitlements file and disabling the auto-generated sandbox entitlement.

# Use full Xcode (CommandLineTools lacks the swift-testing module the tests need).
export DEVELOPER_DIR := /Applications/Xcode.app/Contents/Developer

PROJECT      := authentool.xcodeproj
SCHEME       := authentool
DERIVED      := build
ENTITLEMENTS := authentool/authentool.entitlements
CONFIG       ?= Release
APP          := $(DERIVED)/Build/Products/$(CONFIG)/authentool.app

XCODEBUILD := xcodebuild -project $(PROJECT) -scheme $(SCHEME) \
	-configuration $(CONFIG) -derivedDataPath $(DERIVED) \
	ENABLE_APP_SANDBOX=NO \
	CODE_SIGN_ENTITLEMENTS=$(ENTITLEMENTS)

.PHONY: release debug build run test clean entitlements

# Default target: release build.
release: build

debug:
	$(MAKE) build CONFIG=Debug

build:
	$(XCODEBUILD) build

run: build
	open "$(APP)"

test:
	cd AuthentoolCore && swift test

clean:
	rm -rf $(DERIVED)

# Print the entitlements baked into the built binary (sanity check that print is present).
entitlements:
	codesign -d --entitlements :- "$(APP)" 2>/dev/null
