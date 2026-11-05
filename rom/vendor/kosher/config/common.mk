#
# Everything that makes a KosherFlip build, shared by every product/device.
#

# The three KosherFlip apps (built by apps/ with Gradle, imported as prebuilts)
PRODUCT_PACKAGES += \
    KosherGuard \
    FlipLauncher \
    KosherAI \
    privapp-permissions-kosherflip.xml \
    kosherflip-policy.json

# Build identity
PRODUCT_PRODUCT_PROPERTIES += \
    ro.kosherflip.version=1.0.0 \
    ro.kosherflip.rom=1

# Flip phone friendly defaults
PRODUCT_PRODUCT_PROPERTIES += \
    ro.com.android.dataroaming=false

# No web browser, no search widget: FlipLauncher/KosherGuard override
# Browser2, QuickSearchBox, Launcher3 and Provision (see prebuilt/Android.bp).

# Optional Google apps (Maps, Gmail, Messages, Play services...).
#   WITH_GMS=true requires vendor/gapps (MindTheGapps, branch "baklava" for
#   Android 16) - rom/build-rom.sh adds it to the source tree for you.
#   KosherGuard decides which Google apps are visible (see the policy).
ifeq ($(WITH_GMS),true)
$(call inherit-product-if-exists, vendor/gapps/arm64/arm64-vendor.mk)
endif
