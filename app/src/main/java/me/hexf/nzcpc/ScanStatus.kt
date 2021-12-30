package me.hexf.nzcpc

enum class ScanStatus {
    VALID,
    EXPIRED,
    UNTRUSTED_ISSUER,
    DECODING_FAILED,
    UNKNOWN_KEY,
    INVALID_KEY_FORMAT,
    INVALID_SIGNATURE,
    UNKNOWN_ERROR
}