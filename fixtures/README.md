# Fixtures

All benchmark items are generated completely offline. Construction, signatures, proof-of-work, fixture encoding and validation, node startup, and ledger setup finish before measured publication. Atto reuses pre-encoded request bodies; Nano's shared adapter encodes its RPC envelope during publication. Nano V28.2 and RSNano V3.1 consume the same Nano fixtures.

## Canonical files

| File | Purpose |
| --- | --- |
| `atto-serial-initial.zip` | Atto genesis for the 1 × 1,000 scenario. |
| `atto-serial-benchmark.zip` | Atto 1 × 1,000 pre-encoded measured transactions. |
| `atto-500-initial.zip` | Atto genesis plus setup for the 500 × 100 scenario. |
| `atto-500-benchmark.zip` | Atto 500 × 100 pre-encoded measured transactions. |
| `nano-serial.json` | Nano/RSNano 1 × 1,000 measured state blocks. |
| `nano-500.json` | Nano/RSNano setup and 500 lanes of 100 measured state blocks. |
| `*-verification.json` | Generator provenance, SHA-256 values, counts, distribution, signature/work, and chain validation evidence. |

## Generate and validate

From the repository root:

```bash
./gradlew :app:run --args='generate-fixtures --implementation=all'
./gradlew :app:run --args='validate-fixtures'
```

Select one protocol family with `--implementation=atto` or `--implementation=nano`. Use `--fixtures-dir=PATH` with either command to select a directory.

Generation refuses unsafe in-place overwrites while building candidates. The CLI generates each selected set twice in isolated temporary directories, requires identical file sets and byte-identical contents, validates every candidate, and only then atomically replaces each canonical file.

## Nano fixture schema v2

Nano files declare `nano-v28.2-benchmark-fixture/v2`. The schema records:

- Kotlin/JVM generator provenance and the pinned MIT-licensed JNano source revision used for the small port;
- the Nano V28.2 image, version, and build commit;
- the `dev` network and canonical dev genesis identity;
- a threshold profile indexed by ledger epoch and block subtype;
- the crypto trust boundary and deterministic source/sink account metadata;
- expected setup, lane, and measured counts; and
- SHA-256 checksums for the canonical `source_accounts`, `setup_blocks`, and `measured_lanes` sections.

The complete fixture file also receives a SHA-256 in its verification artifact. Validation recomputes account encodings, state hashes, Ed25519-Blake2b signatures, unsigned work difficulty, previous links, balances, lane distribution, uniqueness, and all checksums.

The V28.2 dev threshold profile is:

| Ledger epoch / subtype | Unsigned threshold |
| --- | --- |
| Epoch 0 or 1, all subtypes | `FE00000000000000` |
| Epoch 2 send/change | `FFC0000000000000` |
| Epoch 2 receive/epoch | `F000000000000000` |

Current canonical fixtures are epoch-0 sends and therefore use `FE00000000000000`; the epoch-2 values remain modeled for future fixtures. Nano's `dev` network is intentional. Its separate `test` network defaults to live-level work and is not used.

The adapted JNano algorithms and dependency licenses are documented in [`benchmark-nano/THIRD_PARTY_NOTICE.md`](../benchmark-nano/THIRD_PARTY_NOTICE.md). The archived JNano Maven artifact is not a dependency.

## Atto fixture provenance

Atto verification artifacts record the generator, Atto Commons 7.0.2, deterministic seed and base timestamp, account/transaction counts, work parallelism, per-ZIP SHA-256, lane distribution, unique hashes, and signature/work validation totals. `workSearchParallelism = 1` means one work-search worker per account lane; the account lanes are generated concurrently. Measured sends are generated in account rounds: every account uses the same timestamp for a given sequence, and the timestamp advances by one millisecond before the next sequence. This keeps timestamps strictly increasing within each account without giving one account an earlier priority range. Each benchmark transaction is stored as a pre-encoded, LF-terminated JSONL object so encoding remains outside timing.

The canonical dev/local-network keys and every deterministic fixture key are public test material and must never protect live funds.
