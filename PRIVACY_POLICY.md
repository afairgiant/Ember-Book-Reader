# Ember Privacy Policy

**Effective date:** October 30, 2026

Ember ("the app") is an EPUB, PDF and audiobook reader for Android, published under the application ID `app.ember.reader`. This policy explains what information the app handles and where it goes.

## Summary

- The developer does not collect, receive, store or sell any of your data.
- Ember has no accounts, no analytics, no advertising and no crash reporting.
- Your books, reading progress, highlights, bookmarks and settings are stored on your device.
- Data leaves your device only when you use a feature that needs it: syncing with a server you set up, connecting to Hardcover, or looking up a word in the dictionary.

## Information stored on your device

Ember keeps the following in its private app storage on your device:

- Books you import or download, and their covers and metadata
- Reading progress, highlights, notes and bookmarks
- Reader and app settings, including fonts you add
- Details of servers you add (name, address, username)
- Passwords and API tokens you enter, encrypted with Android's Keystore
- A random device ID, created the first time you open the app, used to tell your devices apart when syncing progress

Ember only opens files you pick yourself through the Android file picker. It does not scan your storage.

Android's own backup may copy the app's data to your Google account if you have backup turned on. Ember leaves your passwords and tokens out of these backups.

## Information sent off your device

### Servers you configure (Grimmory, OPDS, kosync)

When you add a server, Ember connects to the address you entered to browse your catalog, download and upload books, edit book metadata, and sync reading progress, highlights, notes and bookmarks. Ember sends your username and password (or, for kosync, an MD5 hash of the password), a device name, your device ID, a fingerprint of each book file, and your reading data for that book. This server is run by you or by whoever you got the address from. It is not run by the developer of Ember, and its own privacy practices apply.

If you enter an address starting with `http://` instead of `https://`, this traffic is not encrypted. Use HTTPS for servers outside your home network.

### Hardcover

If you choose to connect a Hardcover account by entering your Hardcover API token, Ember sends that token to Hardcover (`api.hardcover.app`) to read your profile and reading lists, search for books, and show book details and ratings. Hardcover's privacy policy applies: https://hardcover.app/privacy

### Dictionary lookups

When you look up a word, Ember sends that word to a public dictionary service: Wiktionary (Wikimedia Foundation, https://foundation.wikimedia.org/wiki/Policy:Privacy_policy) or the Free Dictionary API (`dictionaryapi.dev`). Only the word is sent.

### Actions that open other apps

Some actions hand text or files to another app or website that you then use directly: web search, translation (Google Translate), opening a Wiktionary page in your browser, and sharing a book or exported notes. Ember sends nothing itself; the app or site you use applies its own privacy policy.

## Permissions

- **Internet and network state:** to connect to your servers, Hardcover and dictionary services.
- **Notifications:** to show download progress and audiobook or read-aloud playback controls.
- **Foreground service:** to keep downloads and audio playback running while the app is in the background.

Read-aloud uses the text-to-speech engine installed on your device. Whether that engine sends text anywhere depends on the engine you have chosen in Android's settings.

## Deleting your data

You can remove books, servers and the Hardcover connection inside the app. Clearing Ember's storage in Android's settings, or uninstalling the app, deletes everything Ember stored on your device. Data you synced to your own server or to Hardcover stays there and must be removed on that service.

## Children

Ember is not directed at children and does not knowingly collect information from anyone, including children under 13.

## Changes to this policy

If this policy changes, the new version will be published at this address with a new effective date.

## Contact

Questions about this policy can be raised as an issue at https://github.com/afairgiant/Ember-Book-Reader/issues
