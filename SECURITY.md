# Security

Raw Photo Converter is alpha software. Security fixes are prepared in the development repository and published in a newer reviewed release; use the latest signed alpha. Older alpha versions do not have a separate maintenance branch.

Do not post exploit details, credentials, private photos, or personal metadata in a public issue. Use GitHub's private vulnerability reporting option on the public repository when available, or contact the maintainer through their GitHub profile to arrange a private reporting channel before sharing sensitive details. Include the affected version, Android/device information, storage provider, impact, and minimal reproduction steps.

Download APKs from the project's GitHub Releases and compare them with the attached SHA256SUMS.txt. Updates use the same signing certificate. Conversion runs locally, does not upload photos, does not modify original RAW files, and uses Android's Storage Access Framework rather than broad all-files access. Metadata preservation can retain GPS and other personal information in output JPEGs; disable it when that information should be omitted.
