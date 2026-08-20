# Cleanroom Relauncher

Relaunches a Forge 1.12.2 instance with [Cleanroom](https://cleanroommc.com).

**Client only at the moment.**

- **Relaunch Now** installs the latest Cleanroom and a compatible Java runtime.
- **Advanced Settings** lets you pick a version, a Java install, and JVM flags.
- Settings can still be changed later in-game via the config menu.

## Installation Notes

- Keep the `!` at the start of the filename so Forge loads the mod first.
- Cleanroom also expects [Fugue](https://www.curseforge.com/minecraft/mc-mods/fugue) to fix some of the old Forge mods.
  - And [Scalar](https://www.curseforge.com/minecraft/mc-mods/scalar-legacy) for mods that used to depend on Scala 2 components.
- Releases are taken from GitHub, with [CleanroomMC Maven](https://repo.cleanroommc.com) as a fallback.
- Downloads and provisioned Java are cached under `~/.cleanroom/` (`relauncher/` and `java/`).
  - Change that parent directory with `-Dcleanroom.homeDir=<path>`.

## Distribution via Modpacks

- Put the jar in `mods/`. Players see the setup window on first launch.
  - To skip that window, ship a filled-in `config/relauncher.json`. Run the pack once locally and copy the file.
  - Automatic Java setup is the usual choice for distribution.
- If the instance is already running Cleanroom, nothing is relaunched.

## Config

Written to `config/relauncher.json`:

- Cleanroom Version
- Automatic Java (version + vendor, default Java 25 / Zulu) or a path to an executable
- Extra JVM Arguments
- Whether to check for newer Cleanroom releases
- Cleanups

## Links

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/cleanroom-relauncher)
- [Modrinth](https://modrinth.com/mod/cleanroom-relauncher)
- [Cleanroom](https://cleanroommc.com)

