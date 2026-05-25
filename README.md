# Utility Finder

Texas electric rate comparison tool. Compares plans across providers using your actual usage data, with support for Time-of-Use rates, TDSP delivery charges, and interval-level billing.

## Installation

Download the installer for your platform from the [Actions artifacts](../../actions) or a tagged release. No Java installation required — the JVM is bundled.

### macOS (.dmg)

1. Open the `.dmg` file
2. Drag **Utility Finder** to your Applications folder
3. **First launch only:** macOS will block the app because it is not from an identified developer.
   Right-click (or Control-click) the app icon and choose **Open**, then click **Open** in the dialog.

   Alternatively, remove the quarantine flag from Terminal:
   ```bash
   xattr -cr "/Applications/Utility Finder.app"
   ```
   After this one-time step the app opens normally.

### Windows (.msi)

Run the `.msi` installer and follow the prompts. A Start Menu shortcut and optional desktop shortcut are created automatically.

### Linux (.deb)

```bash
sudo dpkg -i utility-finder_1.0.0_amd64.deb
```

The app appears in your application launcher. To run from the terminal:
```bash
/opt/utilityfinder/bin/Utility\ Finder
```

## Building from source

Requires JDK 21 and Maven.

```bash
# Run in development mode
mvn javafx:run

# Build fat JAR
mvn package

# Build native installer (current platform only)
mvn package -Pjpackage
# then run jlink + jpackage — see .github/workflows/build-installers.yml
```
