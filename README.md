# txnparse

[![CI](https://github.com/akshar2324/txnparse/actions/workflows/ci.yml/badge.svg)](https://github.com/akshar2324/txnparse/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/akshar2324/txnparse.svg)](https://jitpack.io/#akshar2324/txnparse)

**Turn bank SMS and notifications into structured transactions.** A small Kotlin library for Android and the JVM.

```
"Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 05/01/26 Ref 401234567890"
                                   ↓
Transaction(type=DEBIT, amount=250.00, currency=INR, merchant=SWIGGY,
            accountTail=1234, date=2026-01-05, reference=401234567890, bankId=hdfc)
```

Every expense tracker, budgeting app and personal-finance tool in India ends up writing the same fragile regexes for dozens of bank formats. txnparse puts those formats in **one shared, tested place** that anyone can add to.

## Features

- **Bank rules as JSON.** You can add a bank without writing Kotlin.
- **Self-testing rules.** Each rule file includes sample messages, and CI checks every one of them.
- **Ignores non-transactions:** OTPs, bill-due reminders, UPI collect requests, failed or declined payments, scheduled autopays, and credit card bill payments.
- **Generic fallback** for banks without a rule yet, marked `Confidence.LOW` so you can treat those results with care.
- **Extracts:** amount, debit/credit, merchant or payee, account last digits, balance, date, reference number and channel (UPI / card / ATM / NEFT / IMPS / RTGS).
- **Works offline.** No network calls and no data leaves the device. One dependency (`kotlinx-serialization-json`).

## Install

Available through [JitPack](https://jitpack.io/#akshar2324/txnparse). Add the repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("com.github.akshar2324:txnparse:0.2.1")
}
```

On Android it needs **minSdk 26** (it uses `java.time` and regex named groups).

## Usage

```kotlin
val parser = TransactionParser.withBundledRules()   // build once, reuse; thread-safe

when (val result = parser.parse(body = smsBody, sender = smsSender)) {
    is ParseResult.Parsed -> {
        val txn = result.transaction
        // If the message has no date, txn.date is null: use the SMS timestamp.
        save(txn.copy(date = txn.date ?: smsReceivedDate))
    }
    is ParseResult.Ignored -> log("skipped: ${result.reason}")   // "otp", "due", "failed", ...
    ParseResult.NotATransaction -> Unit
}

// Or just:
val txn: Transaction? = parser.parseOrNull(smsBody, smsSender)
```

Pass the **sender ID** (`VM-HDFCBK`, `AD-ICICIB-S`, ...) whenever you have it. That way only the matching bank's rules are tried, which is faster and avoids crediting a message to the wrong bank.

### Your own rules

```kotlin
val myRule = RuleLoader.fromJson(File("mybank.json").readText())
check(RuleValidator.validate(myRule).isEmpty())             // runs the rule's samples
val parser = TransactionParser.withBundledRules(extraRules = listOf(myRule))  // yours are tried first
```

### Options

```kotlin
TransactionParser(
    rules = RuleLoader.bundledRules(),
    useGenericFallback = false,                                   // only trust bank rules
    ignoreFilter = IgnoreFilter.DEFAULT.plus("promo", Regex("cashback offer", RegexOption.IGNORE_CASE)),
)
```

## Supported banks

| Bank | Rule | Formats | Seen in real messages |
|---|---|---|---|
| Axis Bank | [`axis.json`](src/main/resources/txnparse/rules/axis.json) | UPI debit ✓, NEFT credit | 1 of 2 |
| HDFC Bank | [`hdfc.json`](src/main/resources/txnparse/rules/hdfc.json) | UPI sent ✓, UPI received ✓, net-banking deduction ✓, autopay mandate ✓, card spend | 4 of 5 |
| ICICI Bank | [`icici.json`](src/main/resources/txnparse/rules/icici.json) | UPI debit ✓, UPI credit, card spend | 1 of 3 |
| State Bank of India | [`sbi.json`](src/main/resources/txnparse/rules/sbi.json) | UPI debit ✓ (3 variants), IMPS debit ✓ / credit ✓, transfer debit ✓ / credit ✓, "Debited/Credited INR" ✓, UPI "transfer from" credit | 9 of 10 |
| SBI Card | [`sbicard.json`](src/main/resources/txnparse/rules/sbicard.json) | Card spend | 0 of 1 |

✓ means the format matches a real, redacted message seen in public bug reports or format contributions; the source link is in each sample's `source` field. The other formats are best guesses. If you have real (redacted) messages, please [open a bank format issue](https://github.com/akshar2324/txnparse/issues/new?template=bank-format.yml) or a PR. That's the most useful contribution right now.

Credit card **bill payments** ("Payment of Rs.X received towards your credit card") are ignored by default. They move money between your own accounts, so counting them would double-count your card spends. Refunds to a card still parse.

Other banks still get the generic fallback.

## Contributing

Adding or fixing a bank only takes a JSON file. See [CONTRIBUTING.md](CONTRIBUTING.md).

```bash
./gradlew test
```

## Privacy

Bank messages are sensitive. txnparse never sends data anywhere. **Never paste unredacted messages into issues**: change account digits, reference numbers, names and amounts first.

## License

[MIT](LICENSE)
