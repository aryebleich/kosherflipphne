#
# KosherFlip - Android 16 Generic System Image (GSI) for arm64 flip phones.
#
# A GSI is device independent: it replaces only the "system" partition and
# runs on any Project Treble device with an unlocked bootloader, using the
# phone's own kernel and vendor drivers.
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)
$(call inherit-product, vendor/kosher/config/common.mk)

PRODUCT_NAME := kosher_gsi_arm64
PRODUCT_DEVICE := generic_arm64
PRODUCT_BRAND := KosherFlip
PRODUCT_MODEL := KosherFlip (arm64 GSI)
PRODUCT_MANUFACTURER := KosherFlip
