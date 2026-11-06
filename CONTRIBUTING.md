# Contributing

Thank you! The most useful contribution is **real message formats**. Each new or fixed bank format helps every app that uses txnparse.

## Redact first

Before you paste a message anywhere, replace:

- account and card digits → any other digits (`XX1234` → `XX4821`)
- UPI / RRN / reference numbers → random digits of the same length
- people's names and VPAs → made-up ones (`rahul@okaxis`)
- amounts and balances → other plausible numbers

Keep everything else exactly as it is: spacing, punctuation, capitalisation and line breaks are what the regex depends on.

## Don't want to write regex?

Open a **Bank format** issue with 2 or more redacted messages and the sender ID. Someone will turn it into a rule.

## Adding or fixing a bank rule

1. Create `src/main/resources/txnparse/rules/<bankid>.json`. The id is lowercase, e.g. `kotak`, `pnb`, `bob`.
2. Add the file name to `src/main/resources/txnparse/rules/index.txt`.
3. Run `./gradlew test`.

### Rule file format

```json
{
  "id": "kotak",
  "name": "Kotak Mahindra Bank",
  "country": "IN",
  "currency": "INR",
  "senders": ["KOTAKB"],
  "dateFormats": ["dd-MM-yy"],
  "patterns": [
    {
      "type": "DEBIT",
      "verified": true,
      "regex": "Sent Rs\\.?(?<amount>[\\d,]+(?:\\.\\d{1,2})?) from Kotak Bank AC [xX*]*(?<account>\\d{3,6}) to (?<merchant>.+?) on (?<date>\\d{2}-\\d{2}-\\d{2})\\.UPI Ref (?<ref>\\d+)"
    }
  ],
  "samples": [
    {
      "sender": "VM-KOTAKB",
      "source": "https://github.com/akshar2324/txnparse/issues/<number>",
      "sms": "<redacted real message>",
      "expected": { "type": "DEBIT", "amount": "100.00", "merchant": "...", "account": "1234", "date": "2026-01-05", "ref": "..." }
    }
  ]
}
```

| Field | Notes |
|---|---|
| `senders` | Sender ID fragments. `HDFCBK` matches `VM-HDFCBK`, `AD-HDFCBK-S`, etc. |
| `dateFormats` | [java.time patterns](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns), tried in order. Month names are case-insensitive. Can be overridden per pattern. |
| `patterns[].verified` | Set `true` only if the format matches a real (redacted) message. |
| `patterns[].regex` | Matched case-insensitively against the message with **all whitespace collapsed to single spaces**, so write newlines as a space. |
| Named groups | `amount` (required), `merchant`, `account`, `balance`, `date`, `ref`. |
| `samples` | **Required.** Every pattern must match at least one sample (the tests check this). Fields left out of `expected` aren't checked. |
| `samples[].source` | Optional link to where the real format was seen, such as an issue. |

### Regex tips

- Inside JSON every backslash is doubled: `\d` → `\\d`, `\.` → `\\.`.
- Use lazy `.+?` for merchants, followed by the literal text that comes after the merchant.
- Use `[xX*]*` before account digits, since masking varies (`XX1234`, `*1234`, `XXXXX1234`).
- Start from the bank's distinctive wording, so the pattern doesn't match other banks' messages. The test suite parses every sample with **all** rules loaded and fails if the wrong bank claims it.
- Test regexes quickly at [regex101.com](https://regex101.com) using the **Java 8** flavour.

## Code changes

- Keep the public API small and documented; the build uses Kotlin `explicitApi()`.
- Add a test for every behaviour change.
- The library must stay offline, with no network calls and no analytics.
