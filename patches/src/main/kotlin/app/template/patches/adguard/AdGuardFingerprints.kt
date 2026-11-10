package app.template.patches.adguard

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

// ─────────────────────────────────────────────────────────────────────────────
// Class anchors
// ─────────────────────────────────────────────────────────────────────────────

/**
 * PlusManager class anchor.
 *
 * Anchored on the retry-log string — a developer-written semantic message tied
 * to the retry logic inside PlusManager. Decouples all child fingerprints from
 * the obfuscated class name, which changes every release.
 *
 * Verified stable across:
 *   Latest  phone : [Dynamically Resolved] ← NEW
 *   v4.14.0 phone : D0/b
 *   v4.13.1 phone : F0/b   
 *   v4.13.0 TV    : F0/b
 */
private val PlusManagerClassFingerprint = Fingerprint(
    strings = listOf("Failed to get state from backend. Remaining retry count: "),
)

/**
 * PlusState.PaidLicense class anchor.
 *
 * Anchored on the Kotlin data-class compiler-generated toString prefix.
 * This string is emitted by kotlinc unconditionally and survives R8 shaking.
 *
 * Verified stable across:
 *   Latest  phone : K0/i$n   ← NEW (Confirmed)
 *   v4.14.0 phone : G0/i$n
 *   v4.13.1 phone : I0/i$n   
 *   v4.13.0 TV    : I0/i$n
 */
private val PaidLicenseClassFingerprint = Fingerprint(
    strings = listOf("PaidLicense(licenseKey="),
)

// ─────────────────────────────────────────────────────────────────────────────
// Method fingerprints
// ─────────────────────────────────────────────────────────────────────────────

/**
 * PlusManager.getCachedPlusState() — cache-aside getter.
 *
 * Reads the in-memory cached PlusState field (IGET_OBJECT). On cache miss,
 * fetches from storage, then writes the result back (IPUT_OBJECT). No params,
 * returns a PlusState object. Verified unique: only this method in PlusManager
 * has no params, returns L, and contains both IGET_OBJECT and IPUT_OBJECT.
 *
 * Verified method names across versions:
 *   Latest  phone : Dynamically mapped to return LK0/i
 *   v4.14.0 phone : T6()LG0/i
 *   v4.13.1 phone : P6()LI0/i
 */
internal val GetPlusStateFingerprint = Fingerprint(
    classFingerprint = PlusManagerClassFingerprint,
    returnType = "L",
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        opcode(Opcode.IPUT_OBJECT),
    ),
    custom = { method, _ -> method.parameterTypes.isEmpty() },
)

/**
 * PlusManager.setPlusState(PlusState) — persist + notify.
 *
 * Writes the new PlusState into the in-memory cache field (IPUT_OBJECT),
 * persists it to storage, then notifies all registered observers via an
 * interface call (INVOKE_INTERFACE). One PlusState param, void return.
 */
internal val SetPlusStateFingerprint = Fingerprint(
    classFingerprint = PlusManagerClassFingerprint,
    returnType = "V",
    filters = listOf(
        opcode(Opcode.IPUT_OBJECT),
        opcode(Opcode.INVOKE_INTERFACE),
    ),
    custom = { method, _ -> method.parameterTypes.size == 1 },
)

/**
 * PlusManager.fetchAndUpdatePlusState(cacheStrategy, retryStrategy) — license screen path.
 *
 * Dispatches a coroutine to fetch PlusState from the backend. Drives the
 * AboutLicenseViewModel → license screen StateFlow → UI. Uniquely identified by
 * INSTANCE_OF on the first parameter followed by INVOKE_VIRTUAL. The only
 * 2-param, object-returning method in PlusManager with this shape.
 */
internal val StateFlowResolverFingerprint = Fingerprint(
    classFingerprint = PlusManagerClassFingerprint,
    returnType = "L",
    filters = listOf(
        opcode(Opcode.INSTANCE_OF),
        opcode(Opcode.INVOKE_VIRTUAL),
    ),
    custom = { method, _ -> method.parameterTypes.size == 2 },
)

/**
 * PlusManager.fetchPlusStateForPromo(cacheStrategy, retryStrategy) — promo dialog path.
 *
 * Dispatches a coroutine for the promo/check-license dialog. Free/Unknown result
 * triggers a "Check license" dialog opening the purchase URL in Chrome. Anchored
 * on Kotlin Intrinsics param-name null-check strings at the method top.
 */
internal val PromoStateFlowResolverFingerprint = Fingerprint(
    classFingerprint = PlusManagerClassFingerprint,
    returnType = "L",
    filters = listOf(
        string("cacheStrategy"),
        string("retryStrategy"),
    ),
)

/**
 * PlusState.PaidLicense constructor.
 *
 * Verified class / signature:
 *   Latest  phone : K0/i$n  (String, K0/i$m, K0/i$l, I, I, String) ← NEW
 *   v4.14.0 phone : G0/i$n  (String, G0/i$m, G0/i$l, I, I, String)
 */
internal val PaidLicenseFingerprint = Fingerprint(
    classFingerprint = PaidLicenseClassFingerprint,
    name = "<init>",
)

/**
 * LicenseDuration.Lifetime singleton — toString() method.
 *
 * Used at patch time to resolve the singleton static field via
 * classDef.staticFields.first(). Anchored on string("Lifetime").
 *
 * Verified class names:
 *   Latest  phone : K0/i$l$a ← NEW
 *   v4.14.0 phone : G0/i$l$a
 */
internal val LifetimeDurationFingerprint = Fingerprint(
    name = "toString",
    filters = listOf(
        string("Lifetime"),
    ),
)

/**
 * PlusManager.activateLicenseKey(String) — backend re-verification path.
 *
 * Called on license screen open to re-verify any stored key with the backend.
 * Without a real key this fails and the UI falls back to "License activated"
 * text instead of displaying the "Lifetime" duration label from our synthetic
 * PaidLicense. returnEarly() prevents the backend call entirely.
 */
internal val LicenseKeyActivateFingerprint = Fingerprint(
    classFingerprint = PlusManagerClassFingerprint,
    returnType = "V",
    filters = listOf(
        opcode(Opcode.INVOKE_INTERFACE),
    ),
    custom = { method, _ ->
        method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Ljava/lang/String;"
    },
)