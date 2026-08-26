# Security

## Benchmark-only environments

The harnesses are designed for fresh isolated local/dev ledgers. Do not point them at mainnet nodes, production wallets, or systems holding funds.

The Nano/RSNano harness contains the canonical public dev-network genesis key so an isolated representative can vote. This is published test material, not a production secret. Never reuse benchmark keys or deterministic fixture derivation for live assets.

Before sharing changes, run a secret scanner against the proposed repository and confirm that no Docker endpoints, access tokens, wallet credentials, private infrastructure names, or production keys are present. Keep fixtures limited to documented local/dev networks.
