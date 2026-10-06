# Email and statements

## Connect a mailbox
Hisaab signs in over IMAP with an **app password** (not your normal password).

| Provider | Steps |
|---|---|
| Gmail | Turn on 2-Step Verification, then create a password at myaccount.google.com/apppasswords. |
| Outlook / Hotmail | Open Advanced security options (account.live.com/proofs/manage/additional). Turn on **Two-step verification** first; only then does **App passwords › Create a new app password** appear. Work or school accounts may have app passwords turned off by an admin. |
| Yahoo, iCloud, Zoho | Generate an app password in the account's security settings. |

## Statements read automatically
- Credit card and bank statements from the supported banks.
- CAS from CAMS, KFintech, NSDL and CDSL, including a CAS forwarded by INDmoney ("CDSL Consolidated Account Statement (CAS) across Mutual Funds and Depositories").
- NPS statements (Protean, KFintech, CAMS CRA) and EPF passbooks.

Mails are picked by sender or by subject, so a forwarded statement is read too.

## Locked PDFs
Hisaab tries, in order: passwords you typed before, then common formats built from your profile:
PAN, NAME + DDMM, name + DDMM, DDMMYYYY + last 4 of card, date of birth and mobile variations.
A statement that still will not open shows the email text, which usually says what the password is.
