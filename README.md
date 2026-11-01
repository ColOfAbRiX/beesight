# Beesight

**Beesight** is a Scala 3 command-line tool that analyses skydiving flight data recorded by
[FlySight](https://flysight.ca/), [Airlog One](https://airlog.one/) and [Dekunu](https://dekunu.tech/) devices.
It detects the key flight stages of a jump — takeoff, freefall exit, canopy deployment and landing — computes
per-point flight metrics, analyses swoop landings, and produces trimmed CSV files, per-point metrics exports,
swoop landing reports and interactive HTML charts.

Built on [fs2](https://fs2.io/), [Cats Effect](https://typelevel.org/cats-effect/) and
[Decline](https://ben.kirw.in/decline/), Beesight processes flight data as a streaming pipeline of composable
stages with parallel batch support and optional GraalVM native image compilation.

## Features

- **Pipeline of stages** — `JumpEvents → Cut → Metrics → Swoop → Charts`, exposed as composable CLI commands
- **Automatic stage detection** — Detects takeoff, freefall, canopy and landing transitions from GPS telemetry
- **Per-point flight metrics** — Speeds, course, glide ratio, aerodynamic coefficients, dive and turn rates, energy
- **Swoop landing analysis** — Pattern entry, dive point, turn initiation, rollout start, bottom of turn, stop,
  degrees of rotation, turn direction and duration
- **Interactive charts** — Single-figure HTML chart with toggleable traces, elapsed-time axis and detected points
- **Metadata file cache** — The detection results are cached per file and invalidated independently by the
  configuration that produced it, so repeated or chained commands skip recomputation
- **Multi-format support** — FlySight, Airlog One and Dekunu data, plain or zipped, with conversion to FlySight CSV
- **Parallel batch processing** — Processes multiple files concurrently, with optional limits
- **GraalVM native image** — Compiles to a standalone native binary for fast startup

## Installation

### Prerequisites

- **JDK 21+** (GraalVM required for native image builds)
- **sbt 2.x**

### Build from Source

```bash
# Compile
sbt compile

# Run directly
sbt --server -batch "run --input /path/to/data analyse"

# Run tests
sbt --server -batch "beesight/Test/testOnly *Spec"
```

### Native Image Build

To build a standalone native binary using GraalVM:

```bash
bash build-native.sh
```

The binary will be placed in `dist/beesight.bin`.

## Usage

### Commands

Beesight exposes a pipeline of stages as decline subcommands. When no subcommand is given, `jumpevents` runs.

| Command      | Description                                                              |
|--------------|--------------------------------------------------------------------------|
| `jumpevents` | Detect the jump events and write the processed CSV (default command)     |
| `analyse`    | Run the full analysis: jump events, metrics, swoop and charts            |
| `convert`    | Convert the input files to FlySight CSV format                           |
| `metrics`    | Export the flight metrics of each file as CSV                            |
| `swoop`      | Produce the swoop landing analysis report                                |
| `charts`     | Generate the interactive HTML charts                                     |
| `cut`        | Write the processed CSV trimmed around the detected jump                 |

The `metrics`, `swoop` and `charts` commands automatically run the jump-events detection they depend on. The
`analyse` command runs everything.

The command line is structured as `beesight <global options> <command> <command options>`: global options go
before the command, and each command has its own options after its name — an option given to a command that does
not accept it is an error.

```bash
# Full analysis of a whole directory
sbt --server -batch "run --input /path/to/data analyse"

# Only the swoop report of a single jump
sbt --server -batch "run --input '/path/to/Jump 1234.zip' swoop"

# Convert every file to the FlySight CSV format
sbt --server -batch "run --input '/path/to/data/**/*.csv' convert"

# Trimmed CSV with tuned cut margins
sbt --server -batch "run --input /path/to/data cut --keep-before-takeoff 200 --min-retain-points 0.3"
```

### Global Options

Global options apply to every command and go before the command name.

| Option          | Short | Description                                   | Default |
|-----------------|-------|-----------------------------------------------|---------|
| `--input`       | `-i`  | Input path (file, directory, or glob pattern) | `.`     |
| `--output`      | `-o`  | Output directory for produced files           | The input directory |
| `--limit`       | `-l`  | Maximum number of files to process            | No limit |
| `--parallelism` |       | Number of concurrent files to process         | `1` |
| `--dry-run`     | `-n`  | Run the pipeline without writing any file     | `false` |

### Command Options

Cut options are accepted by `jumpevents`, `analyse` and `cut` — the commands that write the processed CSV.

| Option                   | Description                                                        | Default |
|--------------------------|--------------------------------------------------------------------|---------|
| `--keep-before-takeoff`  | Number of points to keep before a detected takeoff                 | `500`   |
| `--keep-after-landing`   | Number of points to keep after a detected landing                  | `500`   |
| `--min-retain-points`    | Minimum fraction of data points to retain (0.0–1.0)                | `0.1`   |

Detection options are accepted by `jumpevents`, `analyse`, `cut`, `metrics` and `swoop`.

| Option                      | Description                                                        | Default |
|-----------------------------|--------------------------------------------------------------------|---------|
| `--detection-config-path`   | Path to a HOCON file overriding the detection configuration        | —       |
| `--detection-config-string` | HOCON string overriding the detection configuration                | —       |

`charts` and `convert` take no options.

## Output Files

Every input file produces its outputs next to the processed CSV; the metadata file is written next to the input,
keeping its full name (e.g. `jump_1002.zip.meta`).

| Output            | File                          | Produced by            |
|-------------------|-------------------------------|------------------------|
| Processed CSV     | `<base>.csv`                  | `jumpevents`, `cut`    |
| FlySight CSV      | `<base>.flysight.csv`         | `convert`              |
| Metrics CSV       | `<base>.metrics.csv`          | `metrics`              |
| Swoop report      | `<base>.swoop.json`           | `swoop`                |
| Interactive chart | `<base>.html`                 | `charts`               |
| Metadata file     | `<input-full-name>.meta`      | every command          |

### The Metadata File

Each metadata file caches the detection results of its input file: the jump-event points and the cut summary,
together with the SHA-256 hash of the configuration that produced them. A section is reused only when its
configuration is unchanged; stale or corrupt files degrade to an empty cache and are rebuilt. The metrics, swoop
and chart outputs are always recomputed from the input, so the metadata file never duplicates the output files.

## The Chart

The interactive chart is a single Plotly figure over an elapsed-time axis with a checkbox per trace: altitude and
the three speed components are enabled on load; glide ratio, lift/drag coefficients, dive angle and rate, turn
rate, horizontal/vertical acceleration and total energy are rendered only when metrics are available and are off
until ticked. Detected points — takeoff, exit, canopy, landing, and the six swoop points — are marked on the
plot, each group toggleable from the legend.

## Supported Data Formats

| Format        | File types      | Description                                                    |
|---------------|-----------------|----------------------------------------------------------------|
| **FlySight**  | `.csv`, `.zip`  | [FlySight GPS](https://flysight.ca/) data with velocity        |
| **Airlog One**| `.csv`, `.zip`  | [Airlog One](https://airlog.one/) altimeter data               |
| **Dekunu**    | `.csv`, `.zip`  | [Dekunu](https://dekunu.tech/) altimeter data                  |

Input discovery accepts single files, directories, and glob patterns (`*.csv`, `*.zip`, `**/*.csv`, `**/*.zip`).
Zip archives are transparently unarchived.

## Project Structure

```
├── beesight/                # The CLI application (this module)
│   ├── config/              # CLI and detection configuration
│   ├── input/               # CSV/ZIP file reading and discovery
│   ├── metadata/            # Metadata file cache: hashes, JSON codecs
│   ├── output/              # Writers (CSV, chart, report), data cutter, path handling
│   └── pipeline/            # Stage orchestration: runner and per-stage executors
├── beesight-detection/      # Detection library: jump events, metrics, swoop analysis
├── beesight-formats/        # Device formats library: readers, writers, converters
└── beesight-benchmarks/     # JMH benchmarks
```

## Development

### Code Quality

```bash
# Apply formatting and linting fixes
sbt styleApply

# Check formatting and linting
sbt styleCheck
```

## License

Beesight is released under the MIT license. See [LICENSE](LICENSE) for details.

## Author

[Fabrizio Colonna](mailto:colofabrix@tin.it)

## Documentation

- [Detection Algorithm](detection_algorithm.md) — Detailed description of how the flight stage detection works

## See Also

- [Flysight](https://flysight.ca/) — GPS logger for skydivers
- [Flysight File Format](https://flysight.ca/wiki/index.php/File_format) — CSV format documentation
- [Airlog One](https://airlog.one/) — Digital altimeter for skydivers
- [Dekunu](https://dekunu.tech/) — Digital altimeter for skydivers
- [fs2](https://fs2.io/) — Functional streams for Scala
- [Cats Effect](https://typelevel.org/cats-effect/) — Pure asynchronous runtime for Scala
