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
sudo dpkg -i utility-finder_<version>_amd64.deb
```

The app appears in your application launcher. To run from the terminal:
```bash
/opt/utility-finder/bin/Utility\ Finder
```

## Releasing

1. Update the version in `pom.xml`:
   ```xml
   <version>1.1.0</version>
   ```
2. Commit, tag, and push:
   ```bash
   git add pom.xml
   git commit -m "Bump version to 1.1.0"
   git tag v1.1.0
   git push origin main --tags
   ```

Pushing the tag triggers the [Build Native Installers](.github/workflows/build-installers.yml) workflow, which produces `.dmg`, `.msi`, and `.deb` artifacts automatically. Download them from the Actions run summary.

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
