# ADR 0001: DNSCrypt Engine Path

Date: 2026-05-17

Status: Accepted for planning. Implementation remains gated.

## Context

HostShield currently has DNSCrypt groundwork, not a complete DNSCrypt resolver.
`DnsStampParser` parses and encodes DNS stamps, preserves DNSCrypt 32-byte
provider public keys, and handles DNSCrypt relay stamps. `DnsCryptRoutePlanner`
validates resolver/relay stamps and builds Anonymized DNSCrypt relay prefixes.
There is no certificate exchange, Ed25519 provider signature verification,
client key generation, DNSCrypt query encryption/decryption, response
authentication, retry policy, or resolver catalog integration yet.

The user-facing DNSCrypt toggle must stay unavailable until the selected engine
passes the gates in this record.

## Decision

Prefer an audited DNSCrypt engine extraction over a native Kotlin rewrite.

The first implementation spike should extract a narrow DNSCrypt client core from
the upstream DNSCrypt ecosystem, expose it behind a small HostShield-owned Kotlin
facade, and build it as a reproducible Android AAR if Go is used. Embedding the
full `dnscrypt-proxy` daemon is acceptable only as a prototype to validate
correctness and Android packaging costs; it should not become the default product
shape unless binary size, lifecycle, logging, and configuration isolation are
acceptable.

Native Kotlin remains a fallback only if the extracted engine cannot meet app
size, build, licensing, or lifecycle constraints. A native path must use audited
crypto primitives and the same conformance corpus before any release toggle.

## Options Compared

| Option | Strengths | Risks | Decision |
| --- | --- | --- | --- |
| Native Android/Kotlin DNSCrypt | Best integration with existing resolver health, diagnostics, DataStore, and settings. Smallest conceptual runtime footprint. | Highest protocol and crypto implementation risk. HostShield would own Ed25519 certificate verification, X25519 key exchange, XChaCha20/Poly1305 box semantics, padding, nonce discipline, TCP fallback, and Anonymized DNSCrypt relay wrapping. | Reject for first production path. Keep only as fallback after audited primitives and corpus exist. |
| Full `dnscrypt-proxy` via `gomobile` | Upstream implementation already supports DNSCrypt v2, Anonymized DNSCrypt, resolver lists, load balancing, caching, filtering, and Android builds. `gomobile bind` can generate Android AAR bindings for Go packages. | Brings a second runtime, larger binaries, cross-toolchain maintenance, daemon/config model mismatch, and potential overlap with HostShield's existing blocker, cache, diagnostics, and resolver health UI. | Use only as a packaging/proof spike. Do not expose directly as a user-facing daemon by default. |
| Audited library extraction behind Kotlin facade | Reuses upstream protocol behavior while preserving HostShield's app architecture, local diagnostics, resolver health, and blocklist pipeline. Lets the UI depend on a small API: resolve query, refresh certificates, validate route, expose health. | Requires careful extraction boundaries, reproducible AAR/JNI packaging, license review, and ongoing upstream sync. | Chosen direction. Prototype with Go extraction first; keep the facade stable enough to swap implementation if needed. |

## Required Engine Behaviors

### Provider Public Key Validation

The engine must treat the DNS stamp provider public key as trust material, not as
metadata. Certificates fetched from the resolver must be verified with the
pre-distributed provider public key, checked for validity period, filtered to
supported protocol versions, and selected by highest valid serial. HostShield's
current `DnsStampParser` validates that DNSCrypt stamps carry a 32-byte provider
public key; it does not verify certificates.

### Client Public Key Generation

The engine must generate X25519 client keys with a cryptographically secure RNG.
It may rotate per query or per short session, but key reuse must be explicit and
bounded so resolver-side correlation is understood. The Kotlin facade should
make key rotation policy visible in diagnostics.

### Crypto Primitive Equivalence

The current DNSCrypt draft specifies Box-XChaChaPoly using X25519 plus
XChaCha20-Poly1305. Older implementation references and NaCl-style APIs may use
XSalsa20/Poly1305 naming. HostShield must not silently substitute AES-GCM or a
non-equivalent construction. If a native path is chosen later, it must document
the exact primitive mapping and pass published vectors or upstream interop tests.

### Anonymized DNSCrypt Relay Wrapping

The existing `DnsCryptRoutePlanner` can build the relay prefix:

- anon magic: `ff ff ff ff ff ff ff ff 00 00`
- target resolver IP as 16 bytes, with IPv4 mapped into IPv6 form
- target resolver port as big-endian two bytes
- original DNSCrypt query appended unchanged

The final engine must use that wrapping only after the inner DNSCrypt query has
been encrypted for the target resolver. It must reject resolver-as-relay routes
unless the selected privacy mode explicitly allows that weaker topology.

### Replay And Nonces

The engine must generate unique client nonces for each shared secret. Responses
must be authenticated before decryption, and stale or mismatched responses must
be discarded. Diagnostics should expose nonce/replay failures only as aggregate
counters; raw DNS packets and keys must not be logged.

### Timeout And Failover

DNSCrypt must use the same fail-closed posture as pinned DoH. A DNSCrypt provider
failure may fail over only to another validated DNSCrypt route or to the user's
configured non-DNSCrypt fallback policy. It must not silently downgrade to plain
DNS. Resolver health must track certificate refresh failures, authentication
failures, nonce/replay failures, UDP timeout, TCP retry, relay timeout, and final
transport used.

### Test Corpus

Minimum corpus before UI exposure:

- DNS stamp round-trip tests for DNSCrypt resolver stamps and Anonymized DNSCrypt
  relay stamps.
- Route planner tests for IPv4, bracketed IPv6, invalid hostnames, same
  resolver/relay endpoint rejection, and relay prefix bytes.
- Certificate parser tests for signature verification, validity windows, serial
  selection, unsupported protocol versions, malformed TXT records, and expired
  certificates.
- Crypto vectors for key exchange, query encryption, response authentication,
  nonce uniqueness, and padding.
- Interop tests against known public DNSCrypt resolvers from
  `DNSCrypt/dnscrypt-resolvers`, with network tests marked separately from unit
  tests.
- Fault tests for UDP timeout, TCP retry, relay timeout, malformed relay
  responses, replayed responses, and downgrade refusal.

## Implementation Plan

1. Keep `DnsStampParser` and `DnsCryptRoutePlanner` as local preflight
   validation and UI-facing planning code.
2. Add a `DnsCryptEngine` Kotlin interface with no UI toggle:
   `refreshCertificates(route)`, `resolve(route, dnsQuery)`,
   `healthSnapshot()`, and `close()`.
3. Spike an extracted upstream DNSCrypt client core as an Android AAR using
   `gomobile bind`. Measure AAR size, cold-start cost, memory, resolver latency,
   and cancellation behavior.
4. Bind engine health into the existing resolver health/event-log model.
5. Add the test corpus above.
6. Expose a hidden developer setting only after tests pass locally. Promote to a
   normal user-facing toggle only after release builds and on-device smoke tests
   pass on at least arm64 and x86_64.

## Evidence

- Local: `app/app/src/main/java/com/hostshield/util/DnsStampParser.kt`
- Local: `app/app/src/main/java/com/hostshield/service/DnsCryptRoutePlanner.kt`
- Local: `app/app/src/test/java/com/hostshield/service/DnsCryptRoutePlannerTest.kt`
- External: https://github.com/DNSCrypt/dnscrypt-proxy
- External: https://dnscrypt.info/stamps-specifications/
- External: https://dnscrypt.github.io/dnscrypt-protocol/draft-denis-dprive-dnscrypt.html
- External: https://github.com/DNSCrypt/dnscrypt-protocol/blob/master/ANONYMIZED-DNSCRYPT.txt
- External: https://go.dev/wiki/Mobile
- External: https://doc.libsodium.org/secret-key_cryptography/aead/chacha20-poly1305
- External: https://doc.libsodium.org/advanced/stream_ciphers/xchacha20
