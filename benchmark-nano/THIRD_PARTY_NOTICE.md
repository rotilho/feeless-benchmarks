# Third-party notices

## JNano Commons

The Nano account encoding, state-block hashing, Ed25519-Blake2b setup, and work-v1 logic in this module are Kotlin adaptations of the corresponding Java algorithms in JNano Commons. The archived Maven artifact is not used.

- Source: <https://github.com/rotilho/jnano-commons>
- Revision: `ce2bf78a321ee98764117de5dcc230a7466c2502`
- Adapted source files: `NanoAccounts.java`, `NanoAccountEncodes.java`, `NanoBlocks.java`, `ED25519.java`, `NanoSignatures.java`, and `NanoWorks.java`
- License: MIT

```text
MIT License

Copyright (c) 2018 Felipe Rotilho

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Runtime libraries

- Bouncy Castle `bcprov-jdk18on` supplies BLAKE2b. It is distributed under the Bouncy Castle license, an adaptation of the MIT license.
- `net.i2p.crypto:eddsa` supplies EdDSA field and scalar arithmetic. It is distributed under CC0 1.0 Universal.

Nano V28.2 dev-network work thresholds are modeled from <https://github.com/nanocurrency/nano-node/blob/V28.2/nano/lib/constants.cpp#L38-L60> and remain subject to the Nano node repository's license.
