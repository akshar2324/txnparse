# SMS corpus

`bank-sms-samples.json` holds 183 bank SMS with expected outcomes, used by `SmsCorpusTest`.

- 176 cases are synthetic and 7 are adapted from public community posts with every identifier replaced. They cover 52 bank and issuer labels plus general edge cases.
- All account numbers, merchants, references, contacts and OTPs are fictional placeholders. Senders prefixed `TEST-` are labels, not real DLT headers.
- The synthetic messages follow common Indian bank grammar. They are not verified bank-issued templates, so a passing case is not proof that a bank sends that exact text.

`SmsCorpusTest` checks that `ignore` cases never parse, that parsed debits and credits have the right direction, amount and account tail, and that coverage stays at or above `COVERAGE_FLOOR`. When a rule change parses more of the corpus, raise the floor.

To add a case, keep it fictional, give it a unique `id`, set `provenance`, and fill `expected.classification` and `expected.amount`.
